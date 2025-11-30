package ke.co.mpesaforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            val bundle = intent.extras
            if (bundle != null) {
                val pdus = bundle.get("pdus") as Array<*>
                for (pdu in pdus) {
                    val smsMessage = SmsMessage.createFromPdu(pdu as ByteArray)
                    val sender = smsMessage.displayOriginatingAddress
                    val message = smsMessage.displayMessageBody

                    if (isMpesaMessage(sender, message)) {
                        Log.d("MPesaForwarder", "M-Pesa message received: $message")
                        forwardToServer(context, sender, message)
                    }
                }
            }
        }
    }

    private fun isMpesaMessage(sender: String, message: String): Boolean {
        return sender.contains("MPESA", ignoreCase = true) ||
                message.contains("M-PESA", ignoreCase = true) ||
                message.contains("received", ignoreCase = true) ||
                message.contains("paid", ignoreCase = true)
    }

    private fun forwardToServer(context: Context, sender: String, message: String) {
        val prefs = context.getSharedPreferences("MPesaForwarder", Context.MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", "") ?: ""

        if (serverUrl.isEmpty()) {
            Log.e("MPesaForwarder", "Server URL not configured")
            return
        }

        val paymentData = parseMpesaMessage(message)

        Thread {
            try {
                NetworkHelper.sendToServer(serverUrl, paymentData)
                Log.d("MPesaForwarder", "Message forwarded successfully")
            } catch (e: Exception) {
                Log.e("MPesaForwarder", "Failed to forward message: ${e.message}")
            }
        }.start()
    }

    private fun parseMpesaMessage(message: String): Map<String, String> {
        val data = mutableMapOf<String, String>()
        data["raw_message"] = message
        data["timestamp"] = System.currentTimeMillis().toString()

        val amountRegex = """(?:Ksh|KES)\s*(\d+(?:,\d+)*(?:\.\d+)?)""".toRegex(RegexOption.IGNORE_CASE)
        amountRegex.find(message)?.let {
            data["amount"] = it.groupValues[1].replace(",", "")
        }

        val phoneRegex = """(254\d{9}|0[17]\d{8})""".toRegex()
        phoneRegex.find(message)?.let {
            data["phone"] = it.value
        }

        val codeRegex = """[A-Z0-9]{10}""".toRegex()
        codeRegex.find(message)?.let {
            data["transaction_code"] = it.value
        }

        return data
    }
}