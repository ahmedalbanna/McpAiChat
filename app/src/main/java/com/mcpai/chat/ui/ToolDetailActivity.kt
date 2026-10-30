package com.mcpai.chat.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
import com.mcpai.chat.R
import com.mcpai.chat.data.ChatRepository
import com.mcpai.chat.data.ToolApprovalStore
import com.mcpai.chat.mcp.McpTool
import kotlinx.coroutines.launch

/** Lists the tools advertised by the connected MCP servers, with auto-approve toggles. */
class ToolDetailActivity : AppCompatActivity() {

    private lateinit var adapter: ToolAdapter
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tool_detail)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        adapter = ToolAdapter()
        findViewById<RecyclerView>(R.id.toolList).apply {
            layoutManager = LinearLayoutManager(this@ToolDetailActivity)
            adapter = this@ToolDetailActivity.adapter
        }
        emptyView = findViewById(R.id.emptyView)

        val repo = ChatRepository(applicationContext)
        lifecycleScope.launch {
            repo.refreshServers()
            val tools = repo.tools.value
            adapter.submit(tools, repo)
            emptyView.visibility = if (tools.isEmpty()) View.VISIBLE else View.GONE
            repo.close()
        }
    }
}

/** RecyclerView adapter for the tool catalogue with auto-approve toggles. */
class ToolAdapter : RecyclerView.Adapter<ToolAdapter.Holder>() {

    private var tools: List<McpTool> = emptyList()
    private var repo: ChatRepository? = null

    fun submit(tools: List<McpTool>, repo: ChatRepository) {
        this.tools = tools
        this.repo = repo
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = tools.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tool, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val tool = tools[position]
        holder.name.text = tool.name
        holder.description.text = tool.description
        holder.schema.text = tool.inputSchema?.toString(2) ?: "—"

        val serverId = repo?.serverIdOf(tool.name)
        val approvalStore = repo?.toolApprovals
        val isAutoApproved = serverId != null && approvalStore != null &&
            approvalStore.isAutoApproved(serverId, tool.name)

        holder.autoApprove.setOnCheckedChangeListener(null)
        holder.autoApprove.isChecked = isAutoApproved
        holder.autoApprove.setOnCheckedChangeListener { _, checked ->
            if (serverId != null && approvalStore != null) {
                approvalStore.setAutoApproved(serverId, tool.name, checked)
            }
        }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.toolName)
        val description: TextView = view.findViewById(R.id.toolDescription)
        val schema: TextView = view.findViewById(R.id.toolSchema)
        val autoApprove: MaterialSwitch = view.findViewById(R.id.autoApproveSwitch)
    }
}