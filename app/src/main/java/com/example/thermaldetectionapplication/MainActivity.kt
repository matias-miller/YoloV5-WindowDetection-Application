package com.example.thermaldetectionapplication

import android.app.Activity
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.example.thermaldetectionapplication.databinding.ActivityMainBinding
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val homePage = Home()
        val settings = Settings()
        val userGuide = UserGuide()

        setCurrentFragment(homePage)

        bottomNavigationView.setOnItemSelectedListener {
            when(it.itemId) {
                R.id.miHome -> setCurrentFragment(homePage)
                R.id.miUserGuide -> setCurrentFragment(userGuide)
                R.id.miSettings -> setCurrentFragment(settings)
            }
            true
        }
    }

    private fun setCurrentFragment(fragment: Fragment) =
        supportFragmentManager.beginTransaction().apply {
            replace(R.id.flFragment, fragment)
            commit()
        }
}