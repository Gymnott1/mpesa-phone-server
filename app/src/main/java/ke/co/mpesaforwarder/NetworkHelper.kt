package ke.co.mpesaforwarder

import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object NetworkHelper {

    fun sendToServer(serverUrl: String, data: Map<String, String>) {
        val url = URL(serverUrl)
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val jsonObject = JSONObject(data)

            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(jsonObject.toString())
            writer.flush()
            writer.close()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                println("Successfully sent to server")
            } else {
                println("Server returned error: $responseCode")
            }
        } finally {
            connection.disconnect()
        }
    }
}