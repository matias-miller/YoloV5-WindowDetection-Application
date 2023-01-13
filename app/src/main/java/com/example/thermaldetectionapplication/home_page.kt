package com.example.thermaldetectionapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.example.thermaldetectionapplication.databinding.HomePageBinding
import kotlinx.android.synthetic.main.home_page.*


public class Home : Fragment(R.layout.home_page) {

    private lateinit var binding: HomePageBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = HomePageBinding.bind(view)
    }

    private fun pickImageGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"

    }

}