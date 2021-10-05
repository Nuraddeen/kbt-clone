package ng.itcglobal.kabuto.dms

import java.util.Base64

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit

import better.files._
import better.files.Dsl._

import ng.itcglobal.kabuto._
import core.util.{Config, Enum}

import org.scalatest.wordspec.AnyWordSpecLike

class FileManagerServiceSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

   val fileManagerService = testKit.spawn( FileManagerService(), "fileManagerService")
   val probe       = testKit.createTestProbe[FileManagerService.Response]()
   val fileString = "data:image/webp;base64,UklGRkIDAABXRUJQVlA4WAoAAAAgAAAAEgAAEQAASUNDUKACAAAAAAKgbGNtcwQwAABtbnRyUkdCIFhZWiAH5QAJAAEACgAaABJhY3NwQVBQTAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA9tYAAQAAAADTLWxjbXMAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA1kZXNjAAABIAAAAEBjcHJ0AAABYAAAADZ3dHB0AAABmAAAABRjaGFkAAABrAAAACxyWFlaAAAB2AAAABRiWFlaAAAB7AAAABRnWFlaAAACAAAAABRyVFJDAAACFAAAACBnVFJDAAACFAAAACBiVFJDAAACFAAAACBjaHJtAAACNAAAACRkbW5kAAACWAAAACRkbWRkAAACfAAAACRtbHVjAAAAAAAAAAEAAAAMZW5VUwAAACQAAAAcAEcASQBNAFAAIABiAHUAaQBsAHQALQBpAG4AIABzAFIARwBCbWx1YwAAAAAAAAABAAAADGVuVVMAAAAaAAAAHABQAHUAYgBsAGkAYwAgAEQAbwBtAGEAaQBuAABYWVogAAAAAAAA9tYAAQAAAADTLXNmMzIAAAAAAAEMQgAABd7///MlAAAHkwAA/ZD///uh///9ogAAA9wAAMBuWFlaIAAAAAAAAG+gAAA49QAAA5BYWVogAAAAAAAAJJ8AAA+EAAC2xFhZWiAAAAAAAABilwAAt4cAABjZcGFyYQAAAAAAAwAAAAJmZgAA8qcAAA1ZAAAT0AAACltjaHJtAAAAAAADAAAAAKPXAABUfAAATM0AAJmaAAAmZwAAD1xtbHVjAAAAAAAAAAEAAAAMZW5VUwAAAAgAAAAcAEcASQBNAFBtbHVjAAAAAAAAAAEAAAAMZW5VUwAAAAgAAAAcAHMAUgBHAEJWUDggfAAAADAEAJ0BKhMAEgA+MRaIQyIhIRQGqCADBLKAVhfgQGkJLS4K03F2aq98AAD+/OmdvtxT9mtzo6TyHq6/4nzyRz0f5OWb0zDYLfmOvIo5h0eCyqzk96EgUTeEUC+RG8CdXFPA3PYfp/Cqqcf9rji60s/ppVusaT8yPEK6AAA="
   val baseDirectory = Config.filesDirectory
   val splittedTiffsPath = baseDirectory + "/test/splitted-tiffs"
   val sampleTiffsPath = baseDirectory + "/test/sample-tiffs/"


   //copy (or override sample tiffs into the main dir incase if they dont exists there)
   File("data/test/sample-tiffs")
    .copyTo(File(sampleTiffsPath), true)

    //copy the sample single tiff file

 File("data/test/single-tiff/")
    .copyTo(File(baseDirectory+ "/test/single-tiff/"), true)

  import FileManagerService._

  "the FileManagerService" must {

    "split a single tiff file into a dir of files" in {
      rm(File(splittedTiffsPath))
      val split = SplitSingleTiffFile(baseDirectory+ "/test/single-tiff/single.tif", splittedTiffsPath, probe.ref)
      fileManagerService ! split

      probe.expectMessage(FileProcessOK)
    }

    "retrieve files from a dir and return a base64 string image of all images in the dir" in {
      fileManagerService ! GetFileByPath("/test/sample-tiffs/", probe.ref)

      val bytesString = File(sampleTiffsPath).list.toList.map{ tif: File =>
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

      fileManagerService ! appendFile

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

      fileManagerService ! appendFile

      probe.expectMessage(FileResponseError("Invalid file directory"))
    }

    "assert that wrong tiff file will not be saved" in {
      val fileBytes = Base64.getEncoder.encodeToString(File("data/dms/test/test-image-extension/test-gif-file.gif").bytes.toArray)

      val appendFile = AppendFileToDir(
        filename   = "testFile",
        fileString = fileBytes,
        filePath   = "data/dms/test/dir/",
        replyTo    = probe.ref
      )

      fileManagerService ! appendFile

      probe.expectMessage(FileResponseError("Invalid file format gif"))
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

     "successfully save a valid file to a directory" in {

      val filePath = baseDirectory + "/test/"
       
      val saveFileToDir = SaveFileToDir(
      filename = "Test-File-Webp",
      fileString = fileString,
      filePath = filePath,
      extension = Some("webp"),
      replyTo    = probe.ref
      )

      fileManagerService ! saveFileToDir
      probe.expectMessage(FileSavedResponse(filePath+ "Test-File-Webp.webp"))
    }


    "not save an invalid file string" in {

        val filePath =  baseDirectory +"/test/"
        
        val saveFileToDir = SaveFileToDir(
        filename = "Test-File-Webp",
        fileString = "invalid file string",
        filePath = filePath,
        extension = Some("webp"),
        replyTo    = probe.ref
        )

        fileManagerService ! saveFileToDir
        probe.expectMessage(FileResponseError("Could not write file"))
      }


    "not save a file with invalid extension" in {

        val filePath = baseDirectory +"/test/"
        
        val saveFileToDir = SaveFileToDir(
        filename = "Test-File-Webp",
        fileString = fileString,
        filePath = filePath,
        extension = Some("xxx"),
        replyTo    = probe.ref
        )

        fileManagerService ! saveFileToDir
        probe.expectMessage(FileResponseError("Unknown file format"))
      }


  "successfully retrieve the file string of a saved file" in {
      val filePath =  baseDirectory +"/test/"
      val saveFileToDir = SaveFileToDir(
          filename = "Test-File-Webp",
          fileString = fileString,
          filePath = filePath,
          extension = Some("webp"),
          replyTo    = probe.ref
      )

      fileManagerService ! saveFileToDir
      probe.expectMessage(FileSavedResponse(filePath+ "Test-File-Webp.webp"))
   
      val expectedFileString = Base64.getEncoder.encodeToString(File(filePath+ "Test-File-Webp.webp").byteArray)


      fileManagerService ! RetrieveFileString(filePath+ "Test-File-Webp.webp", probe.ref)
      probe.expectMessage(FileRetrievedResponse(expectedFileString))
      

      }
  }
}