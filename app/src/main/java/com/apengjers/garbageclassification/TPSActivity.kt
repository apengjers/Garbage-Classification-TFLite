package com.apengjers.garbageclassification

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.cardview.widget.CardView

class TPSActivity: AppCompatActivity() {

    private fun openMaps(location: String){
        val gmmIntentUri = Uri.parse("geo:0,0?q=${location}")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        startActivity(mapIntent)
    }

    private fun setupMaps(cardId: Int, location: String){
        findViewById<CardView>(cardId).setOnClickListener {
            openMaps(location)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tps)

        setupMaps(R.id.cardBantargebang, "TPST Bantar Gebang")
        setupMaps(R.id.cardCipayung, "TPA Cipayung")
        setupMaps(R.id.cardRawaKucing, "TPS Rawakucing Tangerang")
        setupMaps(R.id.cardSumurBatu, "TPA Sumur Batu")
        setupMaps(R.id.cardGaluga, "TPA Galuga")
    }

}