/**
 * Gateway for SMS and USSD
 * ISC License. 2025. vtamara@pasosdeJesus.org
 *
 * References:
 * https://stackoverflow.com/questions/76897405/how-to-receive-and-send-sms-kotlin-android-studio
 *
 */
package org.pasosdeJesus.gateway

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.registerReceiver
import androidx.core.content.ContextCompat.startActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.pasosdeJesus.gateway.ui.theme.GatewayTheme
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GatewayTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    App(
                        name = "Gateway for stable-sl.pdJ.app",
                        activity = this,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

val JSON: MediaType = "application/json".toMediaType()

var client = OkHttpClient()

@Throws(IOException::class)
fun post(url: String, json: String): String {
    val body: RequestBody = json.toRequestBody(JSON)
    val request: Request = Request.Builder()
        .url(url)
        .post(body)
        .build()
    client.newCall(request).execute().use { response ->
        return response.body!!.string()
    }
}


fun addLog(logs: String, newMsg: String): String {
    val logsList = logs.split("\n").toTypedArray()
    val sdf = SimpleDateFormat("dd/M/yyyy hh:mm:ss")
    val currentDate = sdf.format(Date())
    val res = logsList.plus("[$currentDate] $newMsg")
    if (res.size > 5) {
        res[0] = ""
    }
    val res2 = res.filter { it != "" }.toTypedArray()
    return res2.joinToString(separator = "\n")
}

private fun ussdToCallableUri(ussd: String): Uri? {
    var uriString: String? = ""

    if (!ussd.startsWith("tel:")) uriString += "tel:"

    for (c in ussd.toCharArray()) {
        uriString += if (c == '#') Uri.encode("#")
        else c
    }

    return Uri.parse(uriString)
}


suspend fun fetchApiData(url: String): String {
    return withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .build()


        val response: Response = client.newCall(request).execute()
        if (response.isSuccessful) {
            var ret = response.body?.string() ?: "No data"
            response.close()
            ret
        } else {
            throw IOException("Unexpected code $response")
        }
    }
}

@Composable
fun App(name: String, activity: MainActivity?, modifier: Modifier = Modifier) {
    var recentLogs by remember { mutableStateOf("") }
    var ussdToDial by remember { mutableStateOf("#144*") }
    var smsNumber by remember { mutableStateOf("23275234565") }
    var smsMessage by remember { mutableStateOf("Testing SMS") }

    if (activity != null && ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.SEND_SMS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        recentLogs = addLog(recentLogs, "Permission to send SMS not granted")
    }

    val br = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            for (sms in Telephony.Sms.Intents.getMessagesFromIntent(
                p1
            )) {
                val smsSender = sms.originatingAddress
                val smsMessageBody = sms.displayMessageBody
                recentLogs = addLog(
                    recentLogs,
                    "SMS Received sender: '$smsSender', Message: '$smsMessageBody'"
                )
                //if (smsSender == "the_number_that_you_expect_the_SMS_to_come_FROM") {

                //}
            }
        }
    }

    if (activity != null) {
        registerReceiver(
            activity,
            br,
            IntentFilter("android.provider.Telephony.SMS_RECEIVED"),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    Column {
        Text(
            text = name,
            fontSize = 32.sp
        )

        Text(
            text = recentLogs.toString(),
            modifier = Modifier.height(250.dp)
        )
        Button(
            onClick = { recentLogs = addLog(recentLogs, "Test log entry") }
        ) {
            Text("Test add Log")
        }
        Row {
            TextField(
                value = ussdToDial,
                singleLine = true,
                onValueChange = { ussdToDial = it },
                label = { Text("USSD to dial") }
            )
            Button(onClick = {
                recentLogs = addLog(recentLogs, "Test dial $ussdToDial")
                if (activity != null) {
                    val intent = Intent(Intent.ACTION_CALL, ussdToCallableUri(ussdToDial))
                    startActivity(activity, intent, null)
                }
            }) {
                Text("Test dial USSD")
            }
        }
        Row {
            Column(verticalArrangement = Arrangement.Center) {
                TextField(
                    value = smsNumber,
                    singleLine = true,
                    onValueChange = { smsNumber = it },
                    label = { Text("Number to send SMS") }
                )
                TextField(
                    value = smsMessage,
                    singleLine = true,
                    onValueChange = { smsMessage = it },
                    label = { Text("SMS Message") }
                )
            }
            Button(onClick = {
                recentLogs = addLog(
                    recentLogs,
                    "Sending SMS message '$smsMessage' to '$smsNumber'"
                )
                if (activity != null) {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("smsto:$smsNumber")
                        putExtra("sms_body", smsMessage)
                    }
                    startActivity(activity, intent, null)
                    //val smsManager = SmsManager.getDefault()
                    //smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                }
            }) {
                Text("Send SMS")
            }
        }
        Row {
            Button(onClick = {
                val scope = CoroutineScope(Dispatchers.IO)
                scope.launch {
                    try {
                        val apiData =
                            fetchApiData("https://android-kotlin-fun-mars-server.appspot.com/photos")
                        recentLogs = addLog(
                            recentLogs,
                            "Received from API '$apiData'"
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                        recentLogs = addLog(
                            recentLogs,
                            "Couldn't receive from API"
                        )
                    }
                }

            }) {
                Text("Get from API")
            }
        }
        Row {
            Button(onClick = {
                val scope = CoroutineScope(Dispatchers.IO)
                scope.launch {
                    try {
                        post(url = "https://stable-sl.pdJ.app", json = "{}")
                        recentLogs = addLog(
                            recentLogs,
                            "Posted test"
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                        recentLogs = addLog(
                            recentLogs,
                            "Couldn't post"
                        )
                    }
                }

            }) {
                Text("POST to API")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GatewayPreview() {
    GatewayTheme {
        App(name = "Preview of Gateway for stable-sl.pdJ.app", activity = null)
    }
}
