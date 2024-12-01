package com.nutrigo.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.nutrigo.databinding.FragmentHomeBinding
import com.nutrigo.ui.DetailActivity

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    private lateinit var barcodeScanner: BarcodeScanner

    private var firstCall = true

    // Registrar permintaan izin kamera
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // Jika izin diberikan, mulai kamera
                startCamera()
            } else {
                // Jika izin ditolak, tampilkan pesan ke pengguna
                Toast.makeText(
                    requireContext(),
                    "Izin kamera diperlukan untuk menggunakan fitur ini",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.switchCamera.setOnClickListener {
            cameraSelector =
                if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) CameraSelector.DEFAULT_FRONT_CAMERA
                else CameraSelector.DEFAULT_BACK_CAMERA
            checkCameraPermissionAndStart()
        }

        with(binding) {
            // Mengatur SearchView dengan SearchBar
            searchView.setupWithSearchBar(searchBar)

            // Mengatur keyboard menjadi angka saja di dalam SearchView
            searchView.editText.inputType = InputType.TYPE_CLASS_NUMBER

            // Listener untuk menangani aksi pencarian
            searchView.editText.setOnEditorActionListener { _, _, _ ->
                val inputText = searchView.text.toString()

                if (inputText.isEmpty()) {
                    Toast.makeText(requireContext(), "Input tidak boleh kosong!", Toast.LENGTH_SHORT).show()
                } else {
                    searchBar.setText(inputText)
                    searchView.hide()
                    val intent = Intent(requireContext(), DetailActivity::class.java).apply {
                        putExtra(DetailActivity.PRODUCT_CODE, inputText)
                    }
                    startActivity(intent)

                    searchView.postDelayed({
                        searchView.editText.setText("")
                        searchBar.setText("")
                    }, 200)
                }
                false
            }
        }

        // Cek izin kamera saat pertama kali membuka fragment
        checkCameraPermissionAndStart()
    }

    private fun checkCameraPermissionAndStart() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Jika izin sudah diberikan, langsung mulai kamera
                startCamera()
            }

            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                // Jika pengguna sebelumnya menolak izin, tampilkan dialog penjelasan
                AlertDialog.Builder(requireContext())
                    .setTitle("Izin Kamera Diperlukan")
                    .setMessage("Fitur ini memerlukan izin kamera untuk dapat digunakan. Berikan izin untuk melanjutkan.")
                    .setPositiveButton("OK") { _, _ ->
                        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                    .setNegativeButton("Batal") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .create()
                    .show()
            }

            else -> {
                // Langsung minta izin jika belum pernah diminta sebelumnya
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCamera() {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .build()
        barcodeScanner = BarcodeScanning.getClient(options)

        val analyzer = MlKitAnalyzer(
            listOf(barcodeScanner),
            COORDINATE_SYSTEM_VIEW_REFERENCED,
            ContextCompat.getMainExecutor(requireContext())
        ) { result: MlKitAnalyzer.Result? ->
            showResult(result)
        }

        val cameraController = LifecycleCameraController(requireContext())
        cameraController.setImageAnalysisAnalyzer(
            ContextCompat.getMainExecutor(requireContext()),
            analyzer
        )
        cameraController.bindToLifecycle(viewLifecycleOwner)
        binding.viewFinder.controller = cameraController
    }

    private fun showResult(result: MlKitAnalyzer.Result?) {
        if (firstCall) {
            val barcodeResults = result?.getValue(barcodeScanner)
            if (!barcodeResults.isNullOrEmpty() && barcodeResults[0] != null) {
                val barcode = barcodeResults[0]
                if (barcode.valueType == Barcode.TYPE_PRODUCT) {
                    firstCall = false
                    val productCode = barcode.rawValue

                    val alertDialog = AlertDialog.Builder(requireContext())
                        .setMessage("Kode Produk: $productCode")
                        .setPositiveButton("Detail Nutrisi") { _, _ ->
                            firstCall = true
                            val intent = Intent(requireContext(), DetailActivity::class.java)
                            intent.putExtra(DetailActivity.PRODUCT_CODE, productCode)
                            startActivity(intent)
                        }
                        .setNegativeButton("Scan Lagi") { _, _ ->
                            firstCall = true
                        }
                        .setCancelable(false)
                        .create()
                    alertDialog.show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}