package com.example.auth_yourself

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.content.Intent

class MainActivity : AppCompatActivity() {

    private lateinit var buttonAuth:Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        buttonAuth = findViewById(R.id.button_auth)
        buttonAuth.setOnClickListener {
            // 遷移先のアクティビティを起動するIntentを作成
            val intent = Intent(this, AuthActivity::class.java)
            startActivity(intent)
        }

    }
}