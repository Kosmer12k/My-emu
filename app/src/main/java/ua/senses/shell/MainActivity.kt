package ua.senses.shell

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.TextView
import java.io.File
import java.io.RandomAccessFile

class MainActivity : Activity() {

    private val reqIso = 1

    private lateinit var isoName: TextView
    private lateinit var diskName: TextView
    private lateinit var status: TextView
    private lateinit var log: TextView
    private var process: Process? = null
    private val logLines = ArrayDeque<String>()

    private val dir get() = File(filesDir, "vm").also { it.mkdirs() }
    private val isoFile get() = File(dir, "android.iso")
    private val diskFile get() = File(dir, "hd.img")
    private val biosDir get() = File(filesDir, "pc-bios")

    // Файл рушія: Android кладе його сюди з папки jniLibs
    private val engineFile get() = File(applicationInfo.nativeLibraryDir, "libqemu.so")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        isoName = findViewById(R.id.isoName)
        diskName = findViewById(R.id.diskName)
        status = findViewById(R.id.status)
        log = findViewById(R.id.log)

        refreshNames()

        findViewById<Button>(R.id.btnIso).setOnClickListener { pickIso() }
        findViewById<Button>(R.id.btnDisk).setOnClickListener { createDisk() }
        findViewById<Button>(R.id.btnStart).setOnClickListener { startVm() }
        findViewById<Button>(R.id.btnStop).setOnClickListener { stopVm() }
        findViewById<Button>(R.id.btnVnc).setOnClickListener { openVnc() }
    }

    private fun refreshNames() {
        val prefs = getPreferences(MODE_PRIVATE)
        isoName.text = if (isoFile.exists()) prefs.getString("iso", "android.iso") else getString(R.string.none)
        diskName.text = getString(if (diskFile.exists()) R.string.disk_ready else R.string.disk_none)
    }

    private fun pickIso() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, reqIso)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqIso && resultCode == RESULT_OK) {
            data?.data?.let { copyIso(it) }
        }
    }

    private fun displayName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) return c.getString(i)
        }
        return "android.iso"
    }

    private fun copyIso(uri: Uri) {
        val name = displayName(uri)
        status.text = getString(R.string.st_copying, name)
        Thread {
            try {
                contentResolver.openInputStream(uri)!!.use { input ->
                    isoFile.outputStream().use { out -> input.copyTo(out, 1 shl 20) }
                }
                getPreferences(MODE_PRIVATE).edit().putString("iso", name).apply()
                runOnUiThread {
                    refreshNames()
                    status.text = getString(R.string.st_copied, name)
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = getString(R.string.st_copy_error, e.message ?: "") }
            }
        }.start()
    }

    private fun createDisk() {
        try {
            RandomAccessFile(diskFile, "rw").use { it.setLength(2L * 1024 * 1024 * 1024) }
            refreshNames()
            status.text = getString(R.string.st_disk_created)
        } catch (e: Exception) {
            status.text = getString(R.string.st_error, e.message ?: "")
        }
    }

    // Копіюємо файли BIOS з APK у внутрішню пам’ять (один раз для кожної версії)
    private fun prepareBios() {
        val marker = File(biosDir, ".ready")
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: "0"
        if (marker.exists() && marker.readText() == version) return
        biosDir.deleteRecursively()
        copyAssets("pc-bios", biosDir)
        marker.writeText(version)
    }

    private fun copyAssets(path: String, dest: File) {
        val list = assets.list(path) ?: emptyArray()
        if (list.isEmpty()) {
            dest.parentFile?.mkdirs()
            assets.open(path).use { input -> dest.outputStream().use { input.copyTo(it) } }
        } else {
            dest.mkdirs()
            for (name in list) copyAssets("$path/$name", File(dest, name))
        }
    }

    private fun appendLog(line: String) {
        logLines.addLast(line)
        while (logLines.size > 200) logLines.removeFirst()
        log.text = logLines.joinToString("\n")
    }

    private fun startVm() {
        if (!isoFile.exists()) {
            status.text = getString(R.string.st_no_iso)
            return
        }
        if (!engineFile.exists()) {
            status.text = getString(R.string.st_no_engine)
            return
        }
        if (process != null) return

        logLines.clear()
        log.text = ""
        status.text = getString(R.string.st_preparing)
        Thread {
            try {
                prepareBios()

                val cmd = mutableListOf(
                    engineFile.path,
                    "-L", biosDir.path,
                    "-M", "pc",
                    "-cpu", "qemu32",
                    "-m", "512",
                    "-accel", "tcg",
                    "-vga", "std",
                    "-usb",
                    "-device", "usb-tablet",
                    "-cdrom", isoFile.path
                )
                if (diskFile.exists()) {
                    cmd.add("-hda")
                    cmd.add(diskFile.path)
                }
                cmd.addAll(listOf(
                    "-boot", "d",
                    "-display", "none",
                    "-vnc", "127.0.0.1:0",
                    "-serial", "stdio",
                    "-monitor", "none",
                    "-nic", "none"
                ))

                val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
                process = p
                runOnUiThread { status.text = getString(R.string.st_running) }
                p.inputStream.bufferedReader().forEachLine { line ->
                    runOnUiThread { appendLog(line) }
                }
                val code = p.waitFor()
                process = null
                runOnUiThread { status.text = getString(R.string.st_exited, code.toString()) }
            } catch (e: Exception) {
                process = null
                runOnUiThread { status.text = getString(R.string.st_error, e.message ?: "") }
            }
        }.start()
    }

    private fun stopVm() {
        process?.destroy()
        process = null
        status.text = getString(R.string.st_stopped)
    }

    private fun openVnc() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("vnc://127.0.0.1:5900")))
        } catch (e: Exception) {
            status.text = getString(R.string.st_no_vnc)
        }
    }

    override fun onDestroy() {
        process?.destroy()
        super.onDestroy()
    }
}
