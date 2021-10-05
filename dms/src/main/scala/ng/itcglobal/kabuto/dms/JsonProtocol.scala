package ng.itcglobal.kabuto
package dms
 
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import spray.json.DeserializationException
import spray.json.JsString
import spray.json.JsValue
import spray.json.RootJsonFormat

import ng.itcglobal.kabuto._
import core.util.Enum.HttpResponseStatus
import core.util.Util.KabutoApiHttpResponse


import FileManagerService.Application

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  import JobRepository._

  implicit object StatusFormat extends RootJsonFormat[Status] {
    def write(status: Status): JsValue = status match {
      case Failed     => JsString("Failed")
      case Successful => JsString("Successful")
    }

    def read(json: JsValue): Status = json match {
      case JsString("Failed")     => Failed
      case JsString("Successful") => Successful
      case _                      => throw new DeserializationException("Status unexpected")
    }
  }

  implicit val jobFormat = jsonFormat4(Job)
  implicit val appFormat = jsonFormat2(Application)
  implicit val docDtoFormat = jsonFormat7(DocumentProcessorService.DocumentDto)



   implicit object HttpResponseStatusFormat extends RootJsonFormat[HttpResponseStatus.Value] {

    def write(status: HttpResponseStatus.Value): JsValue =
      status match {
        case HttpResponseStatus.NotFound              => JsString("Not Found") 
        case HttpResponseStatus.NotSupported          => JsString("Not Supported") 
        case HttpResponseStatus.NotAllowed            => JsString("Not Allowed")
        case HttpResponseStatus.DuplicateRequest      => JsString("Duplicate Request")
        case HttpResponseStatus.Success               => JsString("Success")
        case HttpResponseStatus.Failed                => JsString("Failed") 
      }

    def read(json: JsValue): HttpResponseStatus.Value =
      json match {
        case JsString("Not Found")                =>  HttpResponseStatus.NotFound
        case JsString("Not Supported")            =>  HttpResponseStatus.NotSupported
        case JsString("Not Allowed")              =>  HttpResponseStatus.NotAllowed
        case JsString("Duplicate Request")        =>  HttpResponseStatus.DuplicateRequest
        case JsString("Success")                  =>  HttpResponseStatus.Success 
        case JsString("Failed")                   =>  HttpResponseStatus.Failed
        case _                                    =>
          throw new DeserializationException("Status unexpected")
      }

  }

  implicit val httpResponse     = jsonFormat4(KabutoApiHttpResponse)

}