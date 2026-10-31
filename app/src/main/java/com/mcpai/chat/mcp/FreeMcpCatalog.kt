package com.mcpai.chat.mcp

/**
 * A curated catalogue of free, no-authentication remote MCP servers.
 *
 * Every entry was verified live by performing a real `initialize` + `tools/list`
 * handshake against the endpoint. Re-run `tools/verify_catalog.py` in the
 * project to re-check any entry: it parses this file, probes each URL and
 * reports the ones that no longer answer.
 *
 * The tool names are a preview only. The authoritative list is whatever the
 * server reports when the app connects, and the UI shows that instead.
 */
object FreeMcpCatalog {

    data class Entry(
        val name: String,
        val url: String,
        val summary: String,
        val tools: List<String>,
    )

    val entries: List<Entry> = listOf(
        // ---- knowledge and code -------------------------------------------------
        Entry(
            "DeepWiki",
            "https://mcp.deepwiki.com/mcp",
            "Ask questions about any public GitHub repository and read its wiki.",
            listOf("ask_wiki_question", "read_wiki_contents", "read_wiki_structure"),
        ),
        Entry(
            "Context7",
            "https://mcp.context7.com/mcp",
            "Up-to-date library documentation and API usage examples.",
            listOf("resolve-library-id", "query-docs"),
        ),
        Entry(
            "Papers by Ouroboros",
            "https://papers-mcp.vercel.app/mcp",
            "Search academic papers, find related work and format citations.",
            listOf("search_papers", "get_paper", "find_related_papers", "format_citation"),
        ),
        Entry(
            "Hugging Face",
            "https://huggingface.co/mcp",
            "Search models and datasets, browse repositories and read files.",
            listOf("hub_repo_search", "hub_repo_details", "hf_fs", "hf_whoami"),
        ),
        Entry(
            "TranscriptAPI",
            "https://api.transcriptapi.io/mcp",
            "Fetch YouTube transcripts and search channels and playlists.",
            listOf(
                "get_youtube_transcript",
                "search_youtube",
                "get_channel_videos",
                "get_playlist_videos",
            ),
        ),

        // ---- web and documents --------------------------------------------------
        Entry(
            "Firecrawl",
            "https://mcp.firecrawl.dev/v2/mcp",
            "Scrape and parse web pages and run web searches.",
            listOf("firecrawl_scrape", "firecrawl_search", "firecrawl_parse"),
        ),
        Entry(
            "ScrapingBee",
            "https://mcp.scrapingbee.com/mcp",
            "Render pages, get text or HTML, take screenshots and extract data.",
            listOf(
                "get_page_text",
                "get_page_html",
                "get_google_search_results",
                "get_screenshot",
                "extract_page_data",
            ),
        ),
        Entry(
            "agentsvc.io",
            "https://agentsvc.io/mcp",
            "Web scraping and search, IP lookup, translation and SEC filings.",
            listOf(
                "web_scrape",
                "web_scrape_batch",
                "news_search",
                "translate",
                "ip_lookup",
                "sec_filings",
                "find_service",
                "call_service",
            ),
        ),
        Entry(
            "Suprsonic",
            "https://suprsonic.ai/v1/mcp",
            "Search, scrape, people and company data, images and text-to-speech.",
            listOf("search", "scrape", "profiles", "emails", "images", "tts", "stt"),
        ),
        Entry(
            "KDAN PDF",
            "https://mcp.kdandoc.com/mcp",
            "Compare, compress, redact and password-protect PDF documents.",
            listOf("compare_pdfs", "compress_pdf", "upload_pdf", "set_password"),
        ),
        Entry(
            "TheQRCode.io",
            "https://mcp.theqrcode.io/mcp",
            "Generate QR codes and read their analytics.",
            listOf("generate_qr_code", "list_qr_codes", "get_analytics"),
        ),

        // ---- utilities ----------------------------------------------------------
        Entry(
            "Toolrail",
            "https://toolrail.dev/mcp",
            "FX and VAT rates, public holidays and business-day arithmetic.",
            listOf("fx_rates", "fx_convert", "vat_rates", "vat_validate", "days_business_days"),
        ),
        Entry(
            "Sunrise-Sunset.org",
            "https://api.sunrise-sunset.org/mcp",
            "Sunrise, sunset, twilight and day length for any location.",
            listOf("get_sun_times", "get_sun_times_range"),
        ),
        Entry(
            "localScreenshot",
            "https://api.localscreenshot.com/mcp/free",
            "Free screenshot service with proxies in 227 countries.",
            listOf("take_demo_screenshot", "list_countries"),
        ),

        // ---- agent plumbing -----------------------------------------------------
        Entry(
            "Webhook Toolkit",
            "https://webhook-toolkit.com/mcp",
            "Create webhook endpoints, wait for callbacks and inspect payloads.",
            listOf(
                "create_webhook_url",
                "wait_for_webhook",
                "list_webhook_requests",
                "verify_webhook_signature",
            ),
        ),
        Entry(
            "Pushary",
            "https://pushary.com/api/mcp/mcp",
            "Send phone notifications and ask the user questions that block.",
            listOf(
                "send_notification",
                "schedule_reminder",
                "ask_user",
                "wait_for_answer",
                "cancel_question",
            ),
        ),
        Entry(
            "Call Me",
            "https://callmemcp.com/mcp",
            "Place a phone call and exchange text with the user.",
            listOf("call", "say", "text", "wait_for_reply", "hang_up"),
        ),
        Entry(
            "Store API",
            "https://store-api.com/mcp",
            "Ask 58 models and generate images through one endpoint.",
            listOf("ask_model", "list_models", "generate_image", "compare_models"),
        ),
        Entry(
            "Prompt Injection Scanner",
            "https://injectionscan.openkrill.app/mcp",
            "Scan text for prompt-injection patterns before trusting it.",
            listOf("scan_text", "list_injection_rules"),
        ),

        // ---- discovering more servers -------------------------------------------
        Entry(
            "7Maps",
            "https://7it.co.il/7maps/mcp",
            "Live map of public MCP servers: status, tool risk and routing.",
            listOf("find_tool", "road_conditions", "watch", "tool_card"),
        ),
        Entry(
            "Agora",
            "https://openforallofus.com/api/mcp",
            "Search the official MCP registry, ranked by live handshake checks.",
            listOf("search_tools", "get_tool", "submit_report"),
        ),
        Entry(
            "Agent Nexus",
            "https://agentnexus.app/api/public/mcp",
            "Registry of APIs, MCP servers and CLIs with live health checks.",
            listOf("discover_capabilities", "search_registry", "list_entries"),
        ),
        Entry(
            "SkillTide",
            "https://anclum.com/mcp",
            "Search thousands of MCP servers, agent skills and AI projects.",
            listOf("search_tools", "get_tool", "list_categories"),
        ),
        Entry(
            "AI Tools Directory",
            "https://ai-tools-mcp.toolboxes.top/mcp",
            "Search 200+ AI tools by use case, including the free ones.",
            listOf("search_ai_tools", "find_free_ai_tools", "list_departments"),
        ),

        // ---- everyday services --------------------------------------------------
        Entry(
            "Ambee Weather",
            "https://api-mcp-server.ambeedata.com/mcp",
            "Weather, air quality and pollen forecasts.",
            listOf(
                "weather_latest",
                "weather_forecast",
                "air_quality_latest",
                "pollen_forecast",
            ),
        ),
        Entry(
            "Mailcheer",
            "https://mailcheer.com/api/mcp",
            "Send email and manage subscribers, tags and campaigns.",
            listOf("send_email", "list_subscribers", "add_subscriber", "list_campaigns"),
        ),
        Entry(
            "GoodSign",
            "https://goodsign.io/mcp",
            "Send documents for signature, remind signers and download PDFs.",
            listOf("send_from_template", "remind_signers", "download_document"),
        ),
        Entry(
            "Algolab Market Data",
            "https://mcp.algolab.vn/free",
            "Stock quotes, price history, company profiles and research reports.",
            listOf(
                "get_quotes",
                "get_price_history",
                "get_company_profile",
                "get_market_overview",
                "get_technical_signals",
            ),
        ),
        Entry(
            "Lium GPU Marketplace",
            "https://docs.lium.io/mcp",
            "Search and rent GPU pods; read provider pricing.",
            listOf("search_gpus", "get_gpu_prices", "rent_pod"),
        ),
        Entry(
            "WikiBrand",
            "https://wikibrand.org/api/mcp",
            "Logo, colours, typefaces and tone of voice for any brand.",
            listOf("search_brands", "get_brand"),
        ),
        Entry(
            "ViewportWitness",
            "https://qa.honeygate.app/mcp",
            "Browser QA: page checks, accessibility and baseline diffs.",
            listOf("check_page", "verify_page", "compare_page", "extract_page"),
        ),
        Entry(
            "ProfessionalHeadshot",
            "https://professionalheadshot.io/api/mcp",
            "Profile photo specs and headshot guides for LinkedIn, Slack, GitHub.",
            listOf("get_profile_photo_specs", "get_headshot_guide", "audit_headshot"),
        ),
        Entry(
            "Roomvana",
            "https://api.roomvana.co/mcp",
            "Browse room design styles and generate a room design.",
            listOf("list_design_styles", "list_room_types", "design_a_room"),
        ),
    )
}
