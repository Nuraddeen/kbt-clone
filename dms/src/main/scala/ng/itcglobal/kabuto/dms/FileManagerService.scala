package ng.itcglobal.kabuto.dms

import java.io.{File => JFile}
import java.util.Base64
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success, Try}

import akka.actor.typed.{ActorRef, Behavior, Scheduler}
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import better.files._
import better.files.Dsl._

import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.nio.JpegWriter
import com.twelvemonkeys.contrib.tiff.TIFFUtilities

import ng.itcglobal.kabuto._
import ng.itcglobal.kabuto.core.db.postgres.services.{DocumentMetadata, DocumentMetadataDbService}
import ng.itcglobal.kabuto.core.db.postgres.services.DocumentMetadataDbService.SaveDocument
import ng.itcglobal.kabuto.core.util.Enum


/**
 * manager of file IO operation for the kabuto
 */
object FileManagerService {

  implicit val timeout: Timeout = 3.seconds

  sealed trait FileCommand
  final case class AppendFileToDir(
     filename: String,
     fileString: String,
     filePath: String,
     replyTo: ActorRef[FileResponse]) extends FileCommand

  final case class GetFileByPath(dirPath: String, replyTo: ActorRef[FileResponse]) extends FileCommand
  final case class SplitSingleTiffFile(tif: String, outputDir: String, replyTo: ActorRef[FileResponse]) extends FileCommand
  final case class GetSinglePageFromFile(filePath: String, replyTo: ActorRef[FileResponse]) extends FileCommand

  sealed trait FileResponse
  final case object FileProcessOK extends FileResponse
  final case class FileResponseError(msg: String) extends FileResponse
  final case class FileSearchResponse(dir: List[String]) extends FileResponse

  def apply(): Behavior[FileCommand] = Behaviors.receive { (context, message) =>
    implicit val scheduler: Scheduler = context.system.scheduler
    implicit val executionContext: ExecutionContextExecutor = context.executionContext

    message match {
      case req: AppendFileToDir =>
        val fileDir: File = File(req.filePath)

        if (fileDir.isDirectory) {
          Try(Base64.getDecoder.decode(req.fileString)) match {
            case Success(bytes) =>

              getImageExtension(req.fileString) match {
                case Some(x) =>
                  x match {
                    case Enum.ImageTypes.Tiff =>
                      val newFile = File(req.filePath + req.filename + "." + Enum.ImageTypes.Tiff)
                      newFile.writeBytes(bytes.iterator)
                      req.replyTo ! FileProcessOK
                    case _ => 
                      println(s"\n\nInvalid file format $x\n")
                      req.replyTo ! FileResponseError(s"invalid file format $x")

                  }
                case None =>
                  println(s"\n\nunknown file format\n")
                  req.replyTo ! FileResponseError(s"unknown file format")
              }

            case Failure(er) => 
              println(s"\n\ncould not write file $er\n")
              req.replyTo ! FileResponseError(s"could not write file $er")
          }
          Behaviors.same
        } else {
          println(s"\n\ninvalid file directory\n")
          req.replyTo ! FileResponseError("invalid file directory")
          Behaviors.same
        }

      case req: GetFileByPath =>
        val fileDir = File(req.dirPath)
        fileDir.isDirectory match {
          case true =>

            val bytes = fileDir.list.toList.map { tif: File =>
              val byte = resizeTiff(tif)
              Base64.getEncoder.encodeToString(byte)
            }
            req.replyTo ! FileSearchResponse(bytes)
            Behaviors.same
          case false =>
            req.replyTo ! FileSearchResponse(List[String]().empty)
            Behaviors.same
        }

      case req: SplitSingleTiffFile =>
        mkdir(File(req.outputDir))
        Try(TIFFUtilities.split(new JFile(req.tif), new JFile(req.outputDir))) match {
          case Success(x)  => req.replyTo ! FileProcessOK
            Behaviors.same
          case Failure(er) => req.replyTo ! FileResponseError(s"Error while saving file to dir ${er.getMessage}")
            Behaviors.same
        }

      case GetSinglePageFromFile(filePath, replyTo) =>
        val fileDir = File(filePath)
        
        if(fileDir.isEmpty)
          replyTo ! FileResponseError(s"File doesn't exists: $filePath")
        else{
          val byte = Base64.getEncoder.encodeToString(resizeTiff(fileDir))
          replyTo ! FileSearchResponse(List(byte))
        }

        Behaviors.same
      }

    }

  /**
   * resize a tif single tif file to jpeg format in byte arrays
   * @param tif
   * @return
   */
   def resizeTiff(tif: File): Array[Byte] = {
    val image   = ImmutableImage.loader().fromBytes(tif.byteArray)
    val resized = image.scale(0.5)
    resized.bytes(new JpegWriter().withCompression(50).withProgressive(true))
  }

  def getImageExtension(stringImage: String): Option[Enum.ImageTypes.Value] = {
    import Enum._
       stringImage.charAt(0) match {
         case 'i'             => Some(ImageTypes.Png)
         case x @ ('S' | 'T') => Some(ImageTypes.Tiff)
         case 'R'             => Some(ImageTypes.Gif)
         case 'U'             => Some(ImageTypes.Webp)
         case 'Q'             => Some(ImageTypes.Bmp)
         case _               => None
       }
    }
}
