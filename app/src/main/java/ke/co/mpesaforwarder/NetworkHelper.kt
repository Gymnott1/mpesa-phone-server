package ke.co.mpesaforwarder

import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object NetworkHelper {

    fun sendToServer(serverUrl: String, data: Map<String, String>) {
        var connection: HttpURLConnection? = null
        
        try {
            val url = URL(serverUrl)
            connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("User-Agent", "MPesaForwarder/1.0")
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val jsonObject = JSONObject(data)
            val jsonString = jsonObject.toString()

            connection.outputStream.use { outputStream ->
                OutputStreamWriter(outputStream, "UTF-8").use { writer ->
                    writer.write(jsonString)
                    writer.flush()
                }
            }

            val responseCode = connection.responseCode
            
            when (responseCode) {
                HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_CREATED -> {
                    println("✅ Successfully sent to server")
                }
                HttpURLConnection.HTTP_BAD_REQUEST -> {
                    throw Exception("Bad Request (400): Invalid data format")
                }
                HttpURLConnection.HTTP_UNAUTHORIZED -> {
                    throw Exception("Unauthorized (401): Check authentication")
                }
                HttpURLConnection.HTTP_NOT_FOUND -> {
                    throw Exception("Not Found (404): Check server URL")
                }
                HttpURLConnection.HTTP_INTERNAL_ERROR -> {
                    throw Exception("Server Error (500): Internal server error")
                }
                else -> {
                    throw Exception("HTTP $responseCode: ${connection.responseMessage}")
                }
            }
        } catch (e: java.net.UnknownHostException) {
            throw Exception("Cannot resolve hostname")
        } catch (e: java.net.ConnectException) {
            throw Exception("Connection refused - server may be down")
        } catch (e: java.net.SocketTimeoutException) {
            throw Exception("Connection timeout")
        } catch (e: javax.net.ssl.SSLException) {
            throw Exception("SSL certificate error")
        } finally {
            connection?.disconnect()
        }
    }
}