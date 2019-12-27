package com.harshithd.croppedfaces

import ai.fritz.core.Fritz
import ai.fritz.core.FritzOnDeviceModel
import ai.fritz.poseestimationmodelaccurate.PoseEstimationOnDeviceModelAccurate
import ai.fritz.vision.FritzVision
import ai.fritz.vision.FritzVisionImage
import ai.fritz.vision.poseestimation.FritzVisionPosePredictorOptions
import ai.fritz.vision.poseestimation.Pose
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import com.myhexaville.smartimagepicker.ImagePicker
import kotlinx.android.synthetic.main.activity_main.*
import java.io.InputStream


class MainActivity : AppCompatActivity() {

    private val imagePicker by lazy {
        ImagePicker(this, null) { uri ->

            val options = BitmapFactory.Options().apply {
                inMutable = true
            }
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream, null, options)

            val fixedImage = rotateImage(bitmap!!, inputStream!!)
            val canvas = Canvas(fixedImage)

            val visionImage = FritzVisionImage.fromBitmap(fixedImage)
            val poses = determinePoseFromImage(visionImage)
            Log.e("TAG", "${poses.size} poses found!")

            poses.forEach {
                it.draw(canvas)

                it.keypoints.forEachIndexed { index, keypoint ->

                    // Only track keypoints till the shoulder
                    if (index > 6) {
                        return@forEach
                    }

                    // only consider a keypoint as visible if it's score is above 0.2
                    if (keypoint.score < 0.2f) {
                        // if the score is less than 0.2, mark it as not visible
                        tvResult.append("${keypoint.partName} not visible with score ${keypoint.score}\n")
                    }
                }
            }

            ivPickedImage.setImageBitmap(fixedImage)
        }
    }

    // on device Pose Estimation model
    private val onDeviceModel: FritzOnDeviceModel = PoseEstimationOnDeviceModelAccurate()

    // predictor options
    val options = FritzVisionPosePredictorOptions().apply {
        // number of humans to identify, default is 1
        maxPosesToDetect = 10
    }

    // initialize the predictor with the options and model created above
    private val predictor by lazy {
        FritzVision.PoseEstimation.getPredictor(onDeviceModel, options)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Fritz.configure(this, "8587dccaf4a440898a485cd6b02c26fb")
        btnPick.setOnClickListener {
            tvResult.text = ""
            imagePicker.choosePicture(false)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        imagePicker.handleActivityResult(resultCode, requestCode, data)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        imagePicker.handlePermission(requestCode, grantResults)
    }

    private fun determinePoseFromImage(image: FritzVisionImage): List<Pose> {
        val poseResult = predictor.predict(image)
        return poseResult.poses
    }

    fun rotateImage(bitmap: Bitmap, stream: InputStream): Bitmap {
        var rotate = 0f

        val exif =
            ExifInterface(stream)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )

        Log.e("TAG", "Orientation is $orientation")

        if (bitmap.width > bitmap.height) {
            rotate = 90f
        }

        val matrix = Matrix()
        matrix.postRotate(rotate)
        return Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width,
            bitmap.height, matrix, true
        )
    }
}
