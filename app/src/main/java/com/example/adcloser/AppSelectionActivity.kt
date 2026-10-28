package com.example.adcloser

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.adcloser.databinding.ActivityAppSelectionBinding

class AppSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppSelectionBinding
    private val selected = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("ad_closer_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getStringSet("target_packages", setOf("com.outloud.idlefrog")) ?: setOf("com.outloud.idlefrog")
        selected.addAll(saved)

        binding.recyclerApps.layoutManager = LinearLayoutManager(this)
        loadApps()
    }

    private fun loadApps() {
        val pm = packageManager
        val installed = pm.getInstalledApplications(0)
        val allApps = installed
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || it.packageName == "com.outloud.idlefrog" }
            .map {
                AppEntry(
                    label = pm.getApplicationLabel(it).toString(),
                    packageName = it.packageName,
                    icon = pm.getApplicationIcon(it)
                )
            }
            .sortedWith(compareBy<AppEntry> { it.packageName != "com.outloud.idlefrog" }.thenBy { it.label.lowercase() })

        val prefs = getSharedPreferences("ad_closer_prefs", Context.MODE_PRIVATE)
        binding.recyclerApps.adapter = AppAdapter(allApps, selected) { _, _ ->
            prefs.edit().putStringSet("target_packages", selected.toSet()).apply()
        }
    }
}
