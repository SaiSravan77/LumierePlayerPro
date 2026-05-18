package com.lumiere.player.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.lumiere.player.R
import com.lumiere.player.databinding.ActivityLibraryBinding
import com.lumiere.player.db.LumiereDatabase
import com.lumiere.player.db.WatchHistory
import com.lumiere.player.utils.FileScanner
import com.lumiere.player.utils.VideoFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLibraryBinding
    private val db by lazy { LumiereDatabase.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Library"

        setupTabs()
        loadAllVideos()
        loadHistory()
    }

    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("All Videos"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Recent"))
        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                when (tab.position) {
                    0 -> { binding.rvAllVideos.visibility = View.VISIBLE; binding.rvHistory.visibility = View.GONE }
                    1 -> { binding.rvAllVideos.visibility = View.GONE;    binding.rvHistory.visibility = View.VISIBLE }
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })
    }

    private fun loadAllVideos() {
        lifecycleScope.launch {
            binding.progressAll.visibility = View.VISIBLE
            val videos = FileScanner.scanDevice(this@LibraryActivity)
            binding.progressAll.visibility = View.GONE
            binding.rvAllVideos.layoutManager = LinearLayoutManager(this@LibraryActivity)
            binding.rvAllVideos.adapter = VideoAdapter(videos) { video ->
                openVideo(video.uri)
            }
            binding.tvVideoCount.text = "${videos.size} videos found"
        }
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            db.watchHistoryDao().getAll().collectLatest { history ->
                binding.rvHistory.layoutManager = LinearLayoutManager(this@LibraryActivity)
                binding.rvHistory.adapter = HistoryAdapter(history) { item ->
                    openVideo(Uri.parse(item.uriString))
                }
            }
        }
    }

    private fun openVideo(uri: Uri) {
        val intent = Intent(this, MainActivity::class.java).apply { data = uri }
        startActivity(intent)
    }

    override fun onSupportNavigateUp(): Boolean { onBackPressed(); return true }
}

// ─── Adapters ──────────────────────────────────────────────

class VideoAdapter(
    private val items: List<VideoFile>,
    private val onClick: (VideoFile) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VH>() {

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView     = itemView.findViewById(R.id.tvName)
        val tvInfo: TextView     = itemView.findViewById(R.id.tvInfo)
        val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        val thumbnail: android.widget.ImageView = itemView.findViewById(R.id.thumbnail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvName.text = item.name
        holder.tvInfo.text = FileScanner.formatSize(item.size)
        holder.tvDuration.text = FileScanner.formatDuration(item.duration)
        Glide.with(holder.itemView).load(item.uri).centerCrop().into(holder.thumbnail)
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}

class HistoryAdapter(
    private val items: List<WatchHistory>,
    private val onClick: (WatchHistory) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView     = itemView.findViewById(R.id.tvName)
        val tvInfo: TextView     = itemView.findViewById(R.id.tvInfo)
        val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        val thumbnail: android.widget.ImageView = itemView.findViewById(R.id.thumbnail)
        val progressBar: android.widget.ProgressBar = itemView.findViewById(R.id.itemProgress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvName.text = item.fileName
        val pct = if (item.duration > 0) (item.lastPosition * 100 / item.duration).toInt() else 0
        holder.tvInfo.text = "Watched ${pct}%"
        holder.tvDuration.text = formatTime(item.lastPosition)
        holder.progressBar.progress = pct
        Glide.with(holder.itemView).load(Uri.parse(item.uriString)).centerCrop().into(holder.thumbnail)
        holder.itemView.setOnClickListener { onClick(item) }
    }

    private fun formatTime(ms: Long): String {
        val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60)
    }

    override fun getItemCount() = items.size
}
