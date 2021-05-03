package ng.itcglobal.kabuto.dms

import java.util.Base64

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit

import better.files._
import better.files.Dsl._
import File._

import ng.itcglobal.kabuto._

import core.util.Enum

import dms.FileManagerService

import org.scalatest.wordspec.AnyWordSpecLike

class FileManagerServiceSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

   val fileManager = testKit.spawn( FileManagerService(), "fileManagerService")
   val probe       = testKit.createTestProbe[FileManagerService.FileResponse]()
   val fileDestination = "data/xyz/"
   val singleTiffFile  = "data/dms/com-23-56.tif"
  import FileManagerService._

   val getFile = GetFileByPath("data/xxx/", probe.ref)

  "the FileManagerService" must {

    "split a single tiff file into a dir of files" in {
      rm(File(fileDestination))
      val split = SplitSingleTiffFile(singleTiffFile, fileDestination, probe.ref)
      fileManager ! split

      probe.expectMessage(FileProcessOK)
    }

    "retrieve files from a dir and return a base64 string image of all images in the dir" in {
       val dirPath = "data/xxx"
      fileManager ! GetFileByPath(dirPath, probe.ref)

      val bytesString = File(dirPath).list.toList.map{ tif: File =>
        val byte = FileManagerService.resizeTiff(tif)
        java.util.Base64.getEncoder.encodeToString(byte)
      }
      probe.expectMessage(FileSearchResponse(bytesString))

    }

    "append a file into a directory" in {
      val fileBytes = Base64.getEncoder.encodeToString(File("data/dms/test/test-image-extension/test-tiff-file.tiff").bytes.toArray)

      val appendFile = AppendFileToDir(
        filename   = "testFileMoved",
        fileString = fileBytes,
        filePath   = "data/dms/test/dir/",
        replyTo    = probe.ref
      )

      fileManager ! appendFile

      probe.expectMessage(FileProcessOK)

    }

    "assert that wrong dir will throw a FileResponseError" in {
      val fileBytes = Base64.getEncoder.encodeToString(File("data/dms/test/test-image-extension/test-tiff-file.tiff").bytes.toArray)

      println(fileBytes.take(20))
      val appendFile = AppendFileToDir(
        filename   = "testFile",
        fileString = fileBytes,
        filePath   = "data/dms/test/wrong-dir/",
        replyTo    = probe.ref
      )

      fileManager ! appendFile

      probe.expectMessage(FileResponseError("invalid file directory"))
    }

    "assert that wrong tiff file will not be saved" in {
      val fileBytes = Base64.getEncoder.encodeToString(File("data/dms/test/test-image-extension/test-gif-file.gif").bytes.toArray)

      val appendFile = AppendFileToDir(
        filename   = "testFile",
        fileString = fileBytes,
        filePath   = "data/dms/test/dir/",
        replyTo    = probe.ref
      )

      fileManager ! appendFile

      probe.expectMessage(FileResponseError("invalid file format gif"))
    }

    "test image file format in a base64 bit string " in {
      import Enum._
      val gitString = Base64.getEncoder.encodeToString(File("data/dms/test/test-image-extension/test-gif-file.gif").bytes.toArray)
      assert(FileManagerService.getImageExtension(gitString).contains(ImageTypes.Gif))

      val tifString = Base64.getEncoder.encodeToString(File("data/dms/test/test-image-extension/test-tiff-file.tiff").bytes.toArray)
      assert(FileManagerService.getImageExtension(tifString).contains(ImageTypes.Tiff))

      val bmpString = Base64.getEncoder.encodeToString(File("data/dms/test/test-image-extension/test-bmp-file.bmp").bytes.toArray)
      assert(FileManagerService.getImageExtension(bmpString).contains(ImageTypes.Bmp))

      val fileBytes = Base64.getEncoder.encodeToString(File("data/dms/test/testFile.tif").bytes.toArray)
      assert(FileManagerService.getImageExtension(fileBytes).contains(ImageTypes.Tiff))

    }
  }
}