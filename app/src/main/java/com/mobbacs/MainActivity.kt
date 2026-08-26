package com.mobbacs

import com.mobbacs.register.RegisterActivity
import com.mobbacs.login.LoginActivity
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.mobbacs.database.UserRepository
import com.mobbacs.models.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.content.Intent


class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        val intent = Intent(this, LoginActivity()::class.java)
        startActivity(intent)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
    }
}