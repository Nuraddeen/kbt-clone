package ng.itcglobal.kabuto
package dms

import java.io.{File => JFile}
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Base64

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

import akka.actor.typed.{ActorRef, Behavior}
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


  final case class GetAllDirectories(replyTo: ActorRef[FileResponse])
      extends FileCommand
  final case class GetFilesByPath(
      dirPath: String,
      replyTo: ActorRef[FileResponse]
  ) extends FileCommand
  final case class SplitSingleTiffFile(
      tif: String,
      outputDir: String,
      replyTo: ActorRef[FileResponse]
  ) extends FileCommand
  final case class GetSingleDocumentFromApplication(
      filePath: String,
      replyTo: ActorRef[FileResponse]
  ) extends FileCommand

  final case class GetFileByPath(
      dirPath: String,
      replyTo: ActorRef[FileResponse]
  ) extends FileCommand
  final case class GetSinglePageFromFile(
      filePath: String,
      replyTo: ActorRef[FileResponse]
  ) extends FileCommand

  final case class SaveFileToDir(
      filename: String,
      fileString: String,
      filePath: String,
      extension: Option[String] = None,
      replyTo: ActorRef[FileResponse]
  ) extends FileCommand


  final case class RetrieveFileString(
      filePath: String,
      replyTo: ActorRef[FileResponse]
  ) extends FileCommand

  final case class DeleteFile(
        filePath: String,
        replyTo: ActorRef[FileResponse]
    ) extends FileCommand

  sealed trait FileResponse
  final case object FileProcessOK extends FileResponse
  final case class FileResponseError(msg: String) extends FileResponse
  final case class FileSearchResponse(dir: List[String]) extends FileResponse
  final case class AllFilesFetchedResponse(dir: List[Application])
      extends FileResponse
  final case class SingleFileSearchResponse(fileName: String)
      extends FileResponse
  
  final case class FileSavedResponse (fullFilePath: String)  extends FileResponse
  final case class FileRetrievedResponse (fileString: String)  extends FileResponse


  case class Application(name: String, lastModified: String)

  private def getApplicationDirectories(path: String): Try[List[Application]] =
    Try {
      File(path).children
        .filter(_.isDirectory)
        .map(dir =>
          Application(
            dir.name,
            LocalDateTime
              .ofInstant(dir.lastModifiedTime(), ZoneOffset.UTC)
              .toString()
          )
        )
        .toList
    }

  private def getFilesByDirectory(path: String): Try[List[String]] =
    Try {
      val fileDir = File(path)
       
      if (fileDir.isDirectory)
        fileDir.children
          .filter(!_.isDirectory)
          .map(_.name)
          .filter(_.nonEmpty)
          .toList
      else
        List[String]()
    }

  def apply(): Behavior[FileCommand] =
    Behaviors.receive { (context, message) =>
      val log = context.log
      val baseDirectory = Config.filesDirectory

      message match {

        case req: GetFileByPath =>
          val fileDir = File(baseDirectory + req.dirPath)
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

        case GetSinglePageFromFile(filePath, replyTo) =>
          val fileDir = File(filePath)

          if (fileDir.isEmpty)
            replyTo ! FileResponseError(s"File doesn't exists: $filePath")
          else {
            val byte = Base64.getEncoder.encodeToString(resizeTiff(fileDir))
            replyTo ! FileSearchResponse(List(byte))
          }

          Behaviors.same

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
                  log.error(s"unknown file format {}", req)
                  req.replyTo ! FileResponseError(s"unknown file format")
              }

            case Failure(er) => 
              log.error(s"could not write file {} {}", er, req)
              req.replyTo ! FileResponseError(s"could not write file")
          }
          Behaviors.same
        } else {
          log.error(s"invalid file directory {}", req.filePath)
          req.replyTo ! FileResponseError("invalid file directory")
          Behaviors.same
        }

        case req: SaveFileToDir =>
          val fileDir: File = File(req.filePath)

          if(!fileDir.isRegularFile && !fileDir.isDirectory) {
              mkdirs(fileDir)
          }

          if (fileDir.isDirectory) {
            Try (Base64.getMimeDecoder.decode(req.fileString)) match {
              case Success(bytes) =>
                getImageExtension(req.fileString, req.extension) match {
                  case Some(ext) => 
                    val newFile = File(
                      req.filePath + "/" + req.filename + "." + ext.toString 
                    )
                    newFile.writeBytes(bytes.iterator)
                    req.replyTo ! FileSavedResponse(newFile.path.toString)
                   
                  case None =>
                    log.error(s"unknown file format for the extension ${req.extension} for the file ${req.fileString.take(20)} ... ")
                    req.replyTo ! FileResponseError("unknown file format")
                }

              case Failure(error) =>
                log.error(s"could not decode file {}... {}", req.fileString.take(20), error)
                req.replyTo ! FileResponseError("could not write file")
            }
            Behaviors.same
          } else {
            log.error(s"invalid file directory {}", req.filePath)
            req.replyTo ! FileResponseError(s"invalid file directory")
            Behaviors.same
          }

        case req: GetAllDirectories =>
          val defaultFilesPath = Config.filesDirectory

          getApplicationDirectories(defaultFilesPath) match {
            case Success(files) =>
              req.replyTo ! AllFilesFetchedResponse(files)
            case Failure(error) =>
              req.replyTo ! FileResponseError(error.toString)
          }

          Behaviors.same

        case req: GetFilesByPath =>
          getFilesByDirectory(baseDirectory + req.dirPath) match {
            case Success(files) =>
              req.replyTo ! FileSearchResponse(files)
            case Failure(error) =>
              req.replyTo ! FileResponseError(error.toString)
          }

          Behaviors.same

        case req: SplitSingleTiffFile =>
          mkdir(File(req.outputDir))
          Try(
            TIFFUtilities.split(new JFile(req.tif), new JFile(req.outputDir))
          ) match {
            case Success(x) =>
              req.replyTo ! FileProcessOK
              Behaviors.same
            case Failure(er) =>
            log.error(s"Error while saving file to dir {} {}", req.outputDir, er)
              req.replyTo ! FileResponseError(
                s"Error while saving file to dir ${req.outputDir}"
              )
              Behaviors.same
          }

        case req: GetSingleDocumentFromApplication =>
          val document = File(baseDirectory + req.filePath)

          if (document.notExists || document.isDirectory)
            req.replyTo ! FileResponseError(
              s"document '${req.filePath}' does not exists"
            )
          else {
            val documentImage =
              Base64.getEncoder.encodeToString(resizeTiff(document))
            req.replyTo ! SingleFileSearchResponse(documentImage)
          }

          Behaviors.same
      
        case req: RetrieveFileString =>
          val file = File(req.filePath)
          file.exists match {
            case true =>
                  req.replyTo ! FileRetrievedResponse(Base64.getEncoder.encodeToString(file.byteArray) )
              
            case false =>
              log.error(s"The requested file does not exists in the path ${req.filePath}")
              req.replyTo ! FileResponseError(s"File does not exists ")
            }
        Behaviors.same

        case req: DeleteFile =>
              val file = File(req.filePath)
              file.isRegularFile && file.exists match {
                case true =>
                      file.delete() 
                      req.replyTo ! FileProcessOK 
                case false =>
                  log.error(s"Error in processing request $req. The file path is not a file or it does not exists")
                  req.replyTo ! FileResponseError(s"Path is not a file or it does not exists")
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
    val image = ImmutableImage.loader().fromBytes(tif.byteArray)
    val resized = image.scale(0.5)
    resized.bytes(new JpegWriter().withCompression(50).withProgressive(true))
  }

  def getImageExtension(
      stringImage: String,
      extension: Option[String] = None
  ): Option[Enum.ImageTypes.Value] = {
    import Enum._

    extension match {
      case Some(ext) =>
        ext.toUpperCase() match {
          case "PNG"          => Some(ImageTypes.Png)
          case "JPG"         => Some(ImageTypes.Jpg)
          case "JPEG"         => Some(ImageTypes.Jpeg)
          case "TIF" | "TIFF" => Some(ImageTypes.Tiff)
          case "GIF"          => Some(ImageTypes.Gif)
          case "WEBP"         => Some(ImageTypes.Webp)
          case "BMP"          => Some(ImageTypes.Bmp)
          case "PDF"          => Some(ImageTypes.Pdf)
          case _              => None
        }
      case (None) =>
        stringImage.charAt(0) match {
          case 'i'             => Some(ImageTypes.Png)
          case x @ ('S' | 'T') => Some(ImageTypes.Tiff)
          case 'R'             => Some(ImageTypes.Gif)
          case 'U'             => Some(ImageTypes.Webp)
          case 'Q'             => Some(ImageTypes.Bmp)
          case '/'             => Some(ImageTypes.Jpeg)
          case _               => None
          //TODO: capture cases for jpeg, jpg and possibly pdf
        }
    }
  }


   def getFileTypePrefix(fileExtension: String) : String = {
       getImageExtension("", Some(fileExtension)) match {
          case Some(extension) => extension match {
            case  Enum.ImageTypes.Jpeg | Enum.ImageTypes.Jpg | Enum.ImageTypes.Tiff | Enum.ImageTypes.Png | Enum.ImageTypes.Bmp | Enum.ImageTypes.Gif | Enum.ImageTypes.Webp =>
              s"data:image/${extension.toString};base64,"
            case _ => ""
          }
          case None => ""
        }
        
    }

}
