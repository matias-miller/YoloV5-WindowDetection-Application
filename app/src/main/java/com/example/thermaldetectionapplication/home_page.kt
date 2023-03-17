package com.example.thermaldetectionapplication

import android.app.Activity
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.content.res.AssetManager
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.contentValuesOf
import androidx.fragment.app.Fragment
import com.example.thermaldetectionapplication.databinding.HomePageBinding
import kotlinx.android.synthetic.main.home_page.*
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel


public class Home : Fragment(R.layout.home_page) {

    private lateinit var binding: HomePageBinding
    private lateinit var imageView: ImageView
    private lateinit var button: Button
    private lateinit var tvOutput: TextView
    private val GALLERY_REQUEST_CODE = 123

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = HomePageBinding.bind(view)
        imageView = binding.imageView
        button = binding.btnCaptureImage
        tvOutput = binding.tvOutput
        val buttonLoad = binding.btnLoadImage

        button.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                takePicturePreview.launch(null)
            }
            else {
                requestPermission.launch(android.Manifest.permission.CAMERA)
            }
        }
        buttonLoad.setOnClickListener {
            if(ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.READ_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED) {
                val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                intent.type = "image/*"
                val mimeTypes = arrayOf("image/jpeg", "image/png", "image/jpg")
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                onresult.launch(intent)
            } else {
                requestPermission.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

    }

    private fun loadModelFile(modelPath: String, assetManager: AssetManager): ByteBuffer {
        val assetFileDescriptor = assetManager.openFd(modelPath)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    //request camera permission
    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        if (granted) {
            takePicturePreview.launch(null)
        } else {
            Toast.makeText(activity,  "Permission Denied. Try again.", Toast.LENGTH_SHORT).show()
        }
    }

    //launch camera and take picture
    private val takePicturePreview = registerForActivityResult(ActivityResultContracts.TakePicturePreview()){bitmap->
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap)
            //outputGenerator(bitmap)
        }
    }

    //to get image from gallary
    private val onresult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
        Log.i("TAG", "This is the result ${result.data} ${result.resultCode}")
        onResultReceived(GALLERY_REQUEST_CODE, result)
    }

    private fun onResultReceived(requestCode: Int, result: androidx.activity.result.ActivityResult) {
        when(requestCode) {
            GALLERY_REQUEST_CODE->{
                if (result?.resultCode == Activity.RESULT_OK) {
                    result.data?.data?.let{uri->
                        Log.i("TAG", "onResultReceived: $uri")
                        val bitmap = BitmapFactory.decodeStream(context?.contentResolver?.openInputStream(uri))
                        imageView.setImageBitmap(bitmap)
                        outputGenerator(bitmap)
                    }
                } else {
                    Log.e("TAG", "onActivityResult: error in selecting image")
                }
            }
        }
    }

    private fun outputGenerator(bitmap: Bitmap) {
        Thread {
            // Load the TFLite model from file
            val tfliteModel = loadModelFile("ThermalDetectionModelMediumV2.tflite", requireContext())

            // Load the TFLite interpreter from the model
            val options = Interpreter.Options()
            val tflite = Interpreter(tfliteModel, options)
            val inputShape = tflite.getInputTensor(0).shape()
            Log.d(TAG, "Input shape: ${inputShape.contentToString()}")

            // Resize the input bitmap to match the new size
            val resizedBitmap =
                Bitmap.createScaledBitmap(bitmap, inputShape[1], inputShape[2], true)
            Log.d(TAG, "Resized bitmap to ${resizedBitmap.width}x${resizedBitmap.height}.")

            // Convert the input bitmap to a TensorFlow Lite tensor
            val width = inputShape[1]
            val height = inputShape[2]
            val channels = inputShape[3]
            val inputBuffer = ByteBuffer.allocateDirect(width * height * channels * 4)
            inputBuffer.order(ByteOrder.nativeOrder())
            for (x in 0 until width) {
                for (y in 0 until height) {
                    val pixel = resizedBitmap.getPixel(x, y)
                    inputBuffer.putFloat(Color.red(pixel) / 255.0f)
                    inputBuffer.putFloat(Color.green(pixel) / 255.0f)
                    inputBuffer.putFloat(Color.blue(pixel) / 255.0f)
                }
            }
            inputBuffer.rewind()
            val inputTensor = TensorBuffer.createFixedSize(inputShape, DataType.FLOAT32)
            inputTensor.loadBuffer(inputBuffer)
            Log.d(TAG, "Converted input to tensor.")

            // Allocate output tensor buffer
            val outputShape = tflite.getOutputTensor(0).shape()
            val outputTensor = TensorBuffer.createFixedSize(outputShape, DataType.FLOAT32)
            Log.d(TAG, "Allocated output tensor buffer with shape ${outputShape.contentToString()}."
            )

            // Run inference on the input tensor
            tflite.run(inputTensor.buffer, outputTensor.buffer.rewind())
            Log.d(TAG, "Ran inference.")

            // Classify the output tensor as positive or negative
            val score = outputTensor.floatArray[0]
            val prediction = if (score >= 0.01) "Window" else "Not a window"

            // Display the prediction in the UI
            requireActivity().runOnUiThread {
                tvOutput.text = prediction
            }
            Log.d(TAG, "Displayed prediction.")
            Log.d(TAG, "Score: ${score}")

            // Release the TFLite interpreter resources
            tflite.close()
        }.start()

    }

    private fun loadModelFile(modelFileName: String, context: Context): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(modelFileName)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
}