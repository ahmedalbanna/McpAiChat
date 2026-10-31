package com.mcpai.chat.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.mcpai.chat.R
import com.mcpai.chat.mcp.FreeMcpCatalog

/**
 * Browsable catalogue of free, no-authentication remote MCP servers.
 *
 * The list was produced by probing every anonymous endpoint in the
 * awesome-remote-mcp-servers directory with a real `initialize` + `tools/list`
 * handshake, so every entry here is known to answer.
 */
class CatalogActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_catalog)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        findViewById<RecyclerView>(R.id.catalogList).apply {
            layoutManager = LinearLayoutManager(this@CatalogActivity)
            adapter = CatalogAdapter { entry ->
                // The result is handed back to the caller instead of writing the
                // settings here: finishing during a click would leak the touch
                // to the activity underneath and open the wrong screen.
                setResult(
                    RESULT_OK,
                    Intent()
                        .putExtra(EXTRA_URL, entry.url)
                        .putExtra(EXTRA_NAME, entry.name),
                )
                finish()
            }
        }
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_NAME = "name"
    }
}

/** Adapter for the free-server catalogue. */
class CatalogAdapter(
    private val onAdd: (FreeMcpCatalog.Entry) -> Unit,
) : RecyclerView.Adapter<CatalogAdapter.Holder>() {

    private val items = FreeMcpCatalog.entries

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_catalog_server, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = items[position]
        holder.name.text = entry.name
        holder.summary.text = entry.summary
        holder.url.text = entry.url
        holder.tools.text = entry.tools.joinToString(", ")
        holder.add.setOnClickListener { onAdd(entry) }
        holder.itemView.setOnClickListener { onAdd(entry) }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.catalogName)
        val summary: TextView = view.findViewById(R.id.catalogSummary)
        val url: TextView = view.findViewById(R.id.catalogUrl)
        val tools: TextView = view.findViewById(R.id.catalogTools)
        val add: MaterialButton = view.findViewById(R.id.catalogAdd)
    }
}
