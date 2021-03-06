package code.lib

import net.liftweb.json._
import net.liftweb.json.JObject
import net.liftweb.json.JsonDSL._
import net.liftweb.common.Box
import net.liftweb.common.Full
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.JsonAST.JInt
import net.liftweb.json.JDouble
import net.liftweb.common.Empty
import java.net.URL
import org.apache.http.client.HttpClient
import java.net.HttpURLConnection
import net.liftweb.common.Failure
import java.io.BufferedReader
import net.liftweb.util.Helpers._
import java.io.InputStreamReader
import java.util.Date
import net.liftweb.http.RequestVar
import code.lib.ObpJson._
import sun.reflect.generics.reflectiveObjects.NotImplementedException
import java.text.SimpleDateFormat
import net.liftweb.common.Loggable

case class Header(key: String, value: String)

object ObpAPI {
  implicit val formats = DefaultFormats
  val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
  /**
   * The request vars ensure that for one page load, the same API call isn't
   * made multiple times
   */
  object allBanksVar extends RequestVar[Box[BanksJson]] (Empty)
  
  def allBanks : Box[BanksJson]= {
    allBanksVar.get match {
      case Full(a) => Full(a)
      case _ => ObpGet("/banks").flatMap(_.extractOpt[BanksJson])
    }
  }
  
  trait SortDirection {
    val value : String
  }
  object ASC extends SortDirection { val value = "ASC" }
  object DESC extends SortDirection { val value = "DESC" }
  
  /**
   * @return Json for transactions of a particular bank account
   */
  def transactions(bankId: String, accountId: String, viewId: String, limit: Option[Int],
      offset: Option[Int], fromDate: Option[Date], toDate: Option[Date], sortDirection: Option[SortDirection]) : Box[TransactionsJson]= {
    
    val headers : List[Header] = limit.map(l => Header("obp_limit", l.toString)).toList ::: offset.map(o => Header("obp_offset", o.toString)).toList :::
      fromDate.map(f => Header("obp_from_date", dateFormat.format(f))).toList ::: toDate.map(t => Header("obp_to_date", dateFormat.format(t))).toList :::
      sortDirection.map(s => Header("obp_sort_direction", s.value)).toList ::: Nil
    
    ObpGet("/banks/" + urlEncode(bankId) + "/accounts/" + urlEncode(accountId) + "/" + urlEncode(viewId) +
              "/transactions", headers).flatMap(x => x.extractOpt[TransactionsJson])
  }
  
  def publicAccounts(bankId : String) : Box[BarebonesAccountsJson] = {
    ObpGet("/banks/" + urlEncode(bankId) + "/accounts/public").flatMap(_.extractOpt[BarebonesAccountsJson])
  }
  
  def privateAccounts(bankId : String) : Box[BarebonesAccountsJson] = {
    ObpGet("/banks/" + urlEncode(bankId) + "/accounts/private").flatMap(_.extractOpt[BarebonesAccountsJson])
  }
  
  def account(bankId: String, accountId: String, viewId: String) : Box[AccountJson] = {
    ObpGet("/banks/" + urlEncode(bankId) + "/accounts/" + urlEncode(accountId) + "/" + urlEncode(viewId) + "/account").flatMap(x => x.extractOpt[AccountJson])
  }
  
   /**
   * @return The json for the comment if it was successfully added
   */
  def addComment(bankId : String, accountId : String, viewId : String,
      transactionId: String, comment: String) : Box[TransactionCommentJson] = {
    
    val addCommentJson = ("value" -> comment)
    
    val addCommentUrl = "/banks/" + urlEncode(bankId) + "/accounts/" + urlEncode(accountId) + "/" + urlEncode(viewId) +
      "/transactions/" + urlEncode(transactionId) + "/metadata/comments"
    
    ObpPost(addCommentUrl, addCommentJson).flatMap(_.extractOpt[TransactionCommentJson])
  }
  
  def addPermission(bankId: String, accountId: String, userId : String, viewId: String) = {
    val grantPermissionUrl = "/banks/" + urlEncode(bankId) + "/accounts/" + urlEncode(accountId) + "/permissions/" + urlEncode(userId) + "/views/" + urlEncode(viewId)
    ObpPost(grantPermissionUrl, new JObject(Nil))
  }
  
  def addPermissions(bankId: String, accountId: String, userId: String, viewIds : List[String]) : Box[JValue] = {
    val addPermissionsUrl = "/banks/" + urlEncode(bankId) + "/accounts/" + urlEncode(accountId) + "/permissions/" + urlEncode(userId) + "/views"
    val json = ("views" -> viewIds)
    
    ObpPost(addPermissionsUrl, json)
  }
  
  def getPermissions(bankId: String, accountId : String) : Box[PermissionsJson] = {
    ObpGet("/banks/" + bankId + "/accounts/" + accountId + "/permissions").flatMap(x => x.extractOpt[PermissionsJson])
  }
  
  def removePermission(bankId: String, accountId: String, userId : String, viewId: String) = {
    val removePermissionUrl = "/banks/" + urlEncode(bankId) + "/accounts/" + urlEncode(accountId) + "/permissions/" + urlEncode(userId) + "/views/" + urlEncode(viewId)
    ObpDelete(removePermissionUrl)
  }
  
  def removeAllPermissions(bankId: String, accountId: String, userId: String) = {
    val removeAllPermissionsUrl = "/banks/" + urlEncode(bankId) + "/accounts/" + urlEncode(accountId) + "/permissions/" + urlEncode(userId) + "/views"
    ObpDelete(removeAllPermissionsUrl)
  }
  
  /**
   * @return The jsons for the tags that were were successfully added
   */
  def addTags(bankId : String, accountId : String, viewId : String,
      transactionId: String, tags: List[String]) : List[TransactionTagJson] = {
    
    val addTagJsons = tags.map(tag => {
      ("value" -> tag)
    })
    
    val addTagUrl = "/banks/" + urlEncode(bankId) + "/accounts/" + urlEncode(accountId) + "/" + urlEncode(viewId) + "/transactions/" + urlEncode(transactionId) + "/metadata/tags"
    
    addTagJsons.map(addTagJson => ObpPost(addTagUrl, addTagJson).flatMap(_.extractOpt[TransactionTagJson])).flatten
  }
  
  /**
   * @return True if the tag was deleted
   */
  def deleteTag(bankId : String, accountId : String, viewId : String,
      transactionId: String, tagId: String) : Boolean  = {
    val deleteTagUrl = "/banks/" + urlEncode(bankId) + "/accounts/" + urlEncode(accountId) + "/" + urlEncode(viewId) + "/transactions/" + 
      urlEncode(transactionId) + "/metadata/tags/" + urlEncode(tagId)
    ObpDelete(deleteTagUrl)
  }
  
  /**
   * @return The json for the image if it was successfully added
   */
  def addImage(bankId : String, accountId : String, viewId : String,
      transactionId: String, imageURL: String, imageDescription: String) = {

    val json = 
      ("label" -> imageDescription) ~
      ("URL" -> imageURL)
    
    val addImageUrl = "/banks/" + urlEncode(bankId) + "/accounts/" + urlEncode(accountId) + "/" + urlEncode(viewId) + 
      "/transactions/" + urlEncode(transactionId) + "/metadata/images"
      
    ObpPost(addImageUrl, json).flatMap(_.extractOpt[TransactionImageJson])
  }
  
  /**
   * @return True if the image was deleted
   */
  def deleteImage(bankId : String, accountId : String, viewId : String,
      transactionId: String, imageId: String) : Boolean  = {
    
    val deleteImageUrl = "/banks/" + urlEncode(bankId) + "/accounts/" + urlEncode(accountId) + "/" + urlEncode(viewId) + 
      "/transactions/" + urlEncode(transactionId) + "/metadata/images/" + urlEncode(imageId)
    ObpDelete(deleteImageUrl)
  }
}

case class ObpError(error :String)

object ObpPost extends Loggable {
  implicit val formats = DefaultFormats

  def apply(apiPath: String, json : JValue): Box[JValue] = {
    tryo {
      val provider = OAuthClient.defaultProvider //TODO: Support multiple providers
      val credentials = OAuthClient.getAuthorizedCredential(provider)
      val apiUrl = provider.apiBaseUrl
      val url = new URL(apiUrl + apiPath)
      //bleh
      val request = url.openConnection().asInstanceOf[HttpURLConnection] //blagh!
      request.setDoOutput(true)
      request.setRequestMethod("POST")
      request.setRequestProperty("Content-Type", "application/json")
      request.setRequestProperty("Accept", "application/json")
      
      //sign the request if we have some credentials to sign it with
      credentials.foreach(c => c.consumer.sign(request))
      
      //Set the request body
      val output = request.getOutputStream()
      val body = compact(render(json)).getBytes()
      output.write(body)
      output.flush()
      output.close()
      
      request.connect()

      val status = request.getResponseCode()

      //bleh
      val inputStream = if(status >= 400) request.getErrorStream() else request.getInputStream()
      val reader = new BufferedReader(new InputStreamReader(inputStream))
      val builder = new StringBuilder()
      var line = ""
      def readLines() {
        line = reader.readLine()
        if (line != null) {
          builder.append(line + "\n")
          readLines()
        }
      }
      readLines()
      reader.close();
      val result = builder.toString();

      status match {
        case 200 | 201 => parse(result)
        case code => {
          logger.info("Bad response code (" + code + ") from server: " + result)
          val errorMsg = tryo{parse(result).extract[ObpError]}
          
          errorMsg match {
            case Full(e) => throw new Exception(e.error)//bleh -> converts to Failure due to the tryo
            case _ => throw new Exception("Error")
          }
        }
      }
    }
  }
}

//TODO: Almost identical to ObpPost --> refactor
object ObpPut extends Loggable {
  implicit val formats = DefaultFormats

  def apply(apiPath: String, json : JValue): Box[JValue] = {
    tryo {
      val provider = OAuthClient.defaultProvider //TODO: Support multiple providers
      val credentials = OAuthClient.getAuthorizedCredential(provider)
      val apiUrl = provider.apiBaseUrl
      val url = new URL(apiUrl + apiPath)
      //bleh
      val request = url.openConnection().asInstanceOf[HttpURLConnection] //blagh!
      request.setDoOutput(true)
      request.setRequestMethod("PUT")
      request.setRequestProperty("Content-Type", "application/json")
      request.setRequestProperty("Accept", "application/json")
      
      //sign the request if we have some credentials to sign it with
      credentials.foreach(c => c.consumer.sign(request))
      
      //Set the request body
      val output = request.getOutputStream()
      val body = compact(render(json)).getBytes()
      output.write(body)
      output.flush()
      output.close()
      
      request.connect()

      val status = request.getResponseCode()

      //bleh
      val inputStream = if(status >= 400) request.getErrorStream() else request.getInputStream()
      val reader = new BufferedReader(new InputStreamReader(inputStream))
      val builder = new StringBuilder()
      var line = ""
      def readLines() {
        line = reader.readLine()
        if (line != null) {
          builder.append(line + "\n")
          readLines()
        }
      }
      readLines()
      reader.close();
      val result = builder.toString();

      status match {
        case 200 | 201 => parse(result)
        case code => {
          logger.info("Bad response code (" + code + ") from server: " + result)
          val errorMsg = tryo{parse(result).extract[ObpError]}
          
          errorMsg match {
            case Full(e) => throw new Exception(e.error)//bleh -> converts to Failure due to the tryo
            case _ => throw new Exception("Error")
          }
        }
      }
    }
  }
}


object ObpDelete extends Loggable {
  implicit val formats = DefaultFormats
  
  /**
   * @return True if the delete worked
   */
  def apply(apiPath: String): Boolean = {
    val worked = tryo {
      val provider = OAuthClient.defaultProvider //TODO: Support multiple providers
      val credentials = OAuthClient.getAuthorizedCredential(provider)
      val apiUrl = provider.apiBaseUrl
      val url = new URL(apiUrl + apiPath)
      //bleh
      val request = url.openConnection().asInstanceOf[HttpURLConnection] //blagh!
      request.setRequestMethod("DELETE")
      request.setRequestProperty("Content-Type", "application/json")
      request.setRequestProperty("Accept", "application/json")

      //sign the request if we have some credentials to sign it with
      credentials.foreach(c => c.consumer.sign(request))
      request.connect()

      val status = request.getResponseCode()
      
      //bleh
      val inputStream = if(status >= 400) request.getErrorStream() else request.getInputStream()
      val reader = new BufferedReader(new InputStreamReader(inputStream))
      val builder = new StringBuilder()
      var line = ""
      def readLines() {
        line = reader.readLine()
        if (line != null) {
          builder.append(line + "\n")
          readLines()
        }
      }
      readLines()
      reader.close();
      val result = builder.toString();

      status match {
        case 200 | 204 => true
        case code => {
          logger.info("Bad response code (" + code + ") from server: " + result)
          val errorMsg = tryo{parse(result).extract[ObpError]}
          
          errorMsg match {
            case Full(e) => throw new Exception(e.error)//bleh -> converts to Failure due to the tryo
            case _ => throw new Exception("Error")
          }
        }
      }
    }
    
    worked.getOrElse(false)
  }
}

object ObpGet extends Loggable {
  
  implicit val formats = DefaultFormats
  
  //Ah, dispatch does have oauth support. It would be nicer to use dispatch! -E.S.
  def apply(apiPath: String, headers : List[Header] = Nil): Box[JValue] = {
    tryo {
      val provider = OAuthClient.defaultProvider //TODO: Support multiple providers
      val credentials = OAuthClient.getAuthorizedCredential(provider)
      val apiUrl = provider.apiBaseUrl
      val url = new URL(apiUrl + apiPath)
      
      //bleh
      val request = url.openConnection().asInstanceOf[HttpURLConnection] //blagh!
      request.setRequestMethod("GET")
      request.setRequestProperty("Content-Type", "application/json")
      request.setRequestProperty("Accept", "application/json")
      
      headers.foreach(header => request.setRequestProperty(header.key, header.value))

      //sign the request if we have some credentials to sign it with
      credentials.foreach(c => c.consumer.sign(request))
      request.connect()

      val status = request.getResponseCode()
      
      //bleh
      val inputStream = if(status >= 400) request.getErrorStream() else request.getInputStream()
      val reader = new BufferedReader(new InputStreamReader(inputStream))
      val builder = new StringBuilder()
      var line = ""
      def readLines() {
        line = reader.readLine()
        if (line != null) {
          builder.append(line + "\n")
          readLines()
        }
      }
      readLines()
      reader.close();
      val result = builder.toString();
      
      status match {
        case 200 => parse(result)
        case code => {
          logger.info("Bad response code (" + code + ") from server: " + result)
          val errorMsg = tryo{parse(result).extract[ObpError]}
          
          errorMsg match {
            case Full(e) => throw new Exception(e.error)//bleh -> converts to Failure due to the tryo
            case _ => throw new Exception("Error")
          }
        }
      }
    }
  }
}

object ObpJson {
  import net.liftweb.json._
  implicit val formats = DefaultFormats
  case class BanksJson(banks : Option[List[BankJson]]) {
    def bankJsons: List[BankJson] = {
      banks.toList.flatten
    }
  }
  case class BankJson(id: Option[String], 
		  		  short_name: Option[String],
		  		  full_name: Option[String],
		  		  logo: Option[String],
		  		  website: Option[String])
		  		  
  case class UserJson(id: Option[String],
                      provider: Option[String],
		  		      display_name: Option[String])
  
  case class AccountBalanceJson(currency: Option[String],
		  					amount: Option[String])		  	
	
  case class ViewJson(id: Option[String],
		  		  short_name: Option[String],
		  		  description: Option[String],
		  		  is_public: Option[Boolean])
		  		  
  case class AccountJson(id: Option[String],
                     label: Option[String],
		  			 number: Option[String],
		  			 owners: Option[List[UserJson]],
		  			 `type`: Option[String],
		  			 balance: Option[AccountBalanceJson],
		  			 IBAN : Option[String],
		  			 views_available: Option[List[ViewJson]])
		  			 
  case class BarebonesAccountsJson(accounts: Option[List[BarebonesAccountJson]])
  
  case class BarebonesAccountJson(id: Option[String],
		  						  label: Option[String],
		  						  views_available: Option[List[ViewJson]],
		  						  bank_id: Option[String])
		  						  
  case class HolderJson(name: Option[String],
		  				is_alias : Option[Boolean])
		  				
  //TODO: Can this go with BankJson?
  case class LightBankJson(national_identifier: Option[String],
		  			  	   name: Option[String])
  
  case class ThisAccountJson(holders: Option[List[HolderJson]],
		  					 number: Option[String],
		  					 kind: Option[String],
		  					 IBAN: Option[String],
		  					 bank: Option[LightBankJson])
  
  case class LocationJson(latitude: Option[Double],
		  						   longitude: Option[Double],
		  						   date: Option[Date], //TODO: Check if the default date formatter is okay
		  						   user: Option[UserJson])
  
  case class OtherAccountMetadataJson(public_alias: Option[String],
		  							  private_alias: Option[String],
		  							  more_info: Option[String],
		  							  URL: Option[String],
		  							  image_URL: Option[String],
		  							  open_corporates_URL: Option[String],
		  							  corporate_location: Option[LocationJson],
		  							  physical_location: Option[LocationJson])		  					 
		  					 
  //TODO: Why can't an other account have more than one holder?	  					 
  case class OtherAccountJson(id: Option[String],
                              holder: Option[HolderJson],
		  					  number: Option[String],
		  					  kind: Option[String],
		  					  IBAN: Option[String],
		  					  bank: Option[LightBankJson],
		  					  metadata: Option[OtherAccountMetadataJson])
		  					  
  case class OtherAccountsJson(other_accounts: Option[List[OtherAccountJson]])
  
  case class TransactionValueJson(currency: Option[String],
		  						  amount: Option[String])
		  					  
  case class TransactionDetailsJson(`type`: Option[String],
		  							label: Option[String],
		  							posted: Option[Date], //TODO: Check if the default date formatter is okay
		  							completed: Option[Date], //TODO: Check if the default date formatter is okay
		  							new_balance: Option[AccountBalanceJson],
		  							value: Option[TransactionValueJson])	  					  
		  					  
  case class TransactionCommentJson(id: Option[String],
		  				 date: Option[Date], //TODO: Check if the default date formatter is okay
		  				 value: Option[String],
		  				 user: Option[UserJson],
		  				 reply_to: Option[String])
  
  case class TransactionTagJson(id: Option[String],
		  			 date: Option[Date], //TODO: Check if the default date formatter is okay
		  			 value: Option[String],
		  			 user: Option[UserJson])
  
  case class TransactionImageJson(id: Option[String],
		  						  label: Option[String],
		  						  date: Option[Date], //TODO: Check if the default date formatter is okay
		  						  URL: Option[String],
		  						  user: Option[UserJson])
  
  case class TransactionMetadataJson(narrative: Option[String],
		  							 comments: Option[List[TransactionCommentJson]],
		  							 tags: Option[List[TransactionTagJson]],
		  							 images: Option[List[TransactionImageJson]],
		  							 where: Option[LocationJson])
  
  case class TransactionJson(uuid: Option[String],
		  					 id: Option[String],
		  					 this_account: Option[ThisAccountJson],
		  					 other_account: Option[OtherAccountJson],
		  					 details: Option[TransactionDetailsJson],
		  					 metadata: Option[TransactionMetadataJson]) {
    
    lazy val imageJsons : Option[List[TransactionImageJson]] = {
      metadata.flatMap(_.images)
    }
    
    lazy val tagJsons : Option[List[TransactionTagJson]] = {
      metadata.flatMap(_.tags)
    }
    
    lazy val commentJsons : Option[List[TransactionCommentJson]] = {
      metadata.flatMap(_.comments)
    }
  }
  
  case class TransactionsJson(transactions: Option[List[TransactionJson]])
  
  case class PermissionJson(user: Option[UserJson], views: Option[List[ViewJson]])
  
  case class PermissionsJson(permissions : Option[List[PermissionJson]])
  
}