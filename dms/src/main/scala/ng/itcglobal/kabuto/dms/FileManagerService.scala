package ng.itcglobal.kabuto
package dms

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
import core.util.{Config, Enum}


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

  final case class GetAllDirectories(replyTo: ActorRef[FileResponse]) extends FileCommand
  final case class GetFilesByPath(dirPath: String, replyTo: ActorRef[FileResponse]) extends FileCommand
  final case class SplitSingleTiffFile(tif: String, outputDir: String, replyTo: ActorRef[FileResponse]) extends FileCommand
  final case class GetSingleDocumentFromApplication(filePath: String, replyTo: ActorRef[FileResponse]) extends FileCommand

  sealed trait FileResponse
  final case object FileProcessOK extends FileResponse
  final case class FileResponseError(msg: String) extends FileResponse
  final case class FileSearchResponse(dir: List[String]) extends FileResponse
  final case class SingleFileSearchResponse(fileName: String) extends FileResponse

  private def getApplicationDirectories(path: String): Try[List[String]] = Try {
    val fileDir = File(path)

    if(fileDir.isDirectory)
      fileDir
        .children
        .filter(_.isDirectory)
        .map(_.name)
        .toList
    else
      List[String]()
  }

  private def getFilesByDirectory(path: String): Try[List[String]] = Try {
    val fileDir = File(path)

    if(fileDir.isDirectory)
      fileDir
        .children
        .filter(!_.isDirectory)
        .map(child => {
          Base64.getEncoder.encodeToString(resizeTiff(child))
        })
        .toList
    else
      List[String]()
  }

  def apply(): Behavior[FileCommand] = Behaviors.receive { (context, message) =>
    val log = context.log

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
                      req.replyTo ! FileResponseError(s"invalid file format $x")

                  }
                case None =>
                  log.error("unknown file format")
                  req.replyTo ! FileResponseError(s"unknown file format")
              }

            case Failure(er) => 
              log.error(s"could not write file $er")
              req.replyTo ! FileResponseError(s"could not write file $er")
          }
          Behaviors.same
        } else {
          log.error(s"invalid file directory", req.filePath)
          req.replyTo ! FileResponseError("invalid file directory")
          Behaviors.same
        }

      case req: GetAllDirectories =>
        val defaultFilesPath = Config.filesDirectory
        
        // FileManger get the list of all dirs from default location
        getApplicationDirectories(defaultFilesPath) match {
          case Success(files) => req.replyTo ! FileSearchResponse(files)
          case Failure(error) => 
            println(s"$error,\n")
            req.replyTo ! FileResponseError(error.toString)
        }
        
        Behaviors.same

      case req: GetFilesByPath =>
        val filePath = Config.filesDirectory + req.dirPath

        getFilesByDirectory(filePath) match {
          case Success(files) => req.replyTo ! FileSearchResponse(files)
          case Failure(error) => req.replyTo ! FileResponseError(error.toString)
        }

        Behaviors.same

      case req: SplitSingleTiffFile =>
        mkdir(File(req.outputDir))
        Try(TIFFUtilities.split(new JFile(req.tif), new JFile(req.outputDir))) match {
          case Success(x)  => req.replyTo ! FileProcessOK
            Behaviors.same
          case Failure(er) => req.replyTo ! FileResponseError(s"Error while saving file to dir ${er.getMessage}")
            Behaviors.same
        }

      case req: GetSingleDocumentFromApplication =>
        val fileDir = File(req.filePath)
        
        if(fileDir.notExists || fileDir.isDirectory)
          req.replyTo ! FileResponseError(s"file '${req.filePath}' does not exists")
        else{
          val byte = Base64.getEncoder.encodeToString(resizeTiff(fileDir))
          req.replyTo ! SingleFileSearchResponse(byte)
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
