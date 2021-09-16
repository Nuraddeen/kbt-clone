package ng.itcglobal.kabuto
package core.util

import  java.security.MessageDigest


import spray.json.JsValue

import  ng.itcglobal.kabuto._
import  core.util.Enum.HttpResponseStatus
 


object Util {

  case class BetasoftApiHttpResponse(
    status: HttpResponseStatus.Value,
    description: String,
    code: Option[Int]     = None,
    data: Option[JsValue] = None)



  def md5(text: String): String = {
        MessageDigest
        .getInstance("MD5")
        .digest(text.getBytes())
        .toString()

    }

    /**
      * compares the equality of 2 strings using their md5 hash
      *
      * @param text1
      * @param text2
      * @return Boolean
      */
    def compareMd5(text1: String, text2: String) : Boolean ={
      md5(text1) equals md5(text2)
    }

}
