package com.klikdash.app;

import android.content.Context;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MaintenanceServer {
    public static final int PORT = 8765;
    private final Context ctx;
    private final Runnable onDataChanged;
    private ServerSocket serverSocket;
    private ExecutorService pool;
    private volatile boolean running;

    public MaintenanceServer(Context ctx, Runnable onDataChanged) {
        this.ctx = ctx.getApplicationContext();
        this.onDataChanged = onDataChanged;
    }

    public boolean isRunning() {
        return running;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        pool = Executors.newCachedThreadPool();
        pool.execute(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                while (running) {
                    Socket client = serverSocket.accept();
                    pool.execute(() -> handleClient(client));
                }
            } catch (Throwable ignored) {
                running = false;
            }
        });
    }

    public synchronized void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Throwable ignored) {}
        if (pool != null) pool.shutdownNow();
    }

    private void handleClient(Socket s) {
        try (Socket sock = s;
             BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
             OutputStream out = sock.getOutputStream()) {
            String line = in.readLine();
            if (line == null) return;
            String[] parts = line.split(" ");
            String method = parts.length > 0 ? parts[0] : "GET";
            String path = parts.length > 1 ? parts[1] : "/";

            int contentLength = 0;
            String h;
            while ((h = in.readLine()) != null && !h.isEmpty()) {
                if (h.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(h.substring(15).trim());
                }
            }

            if ("GET".equals(method) && "/api/data".equals(path)) {
                byte[] body = MaintenanceStore.load(ctx).toString().getBytes(StandardCharsets.UTF_8);
                writeResponse(out, "200 OK", "application/json; charset=utf-8", body);
            } else if ("GET".equals(method) && "/api/trips".equals(path)) {
                JSONObject res = new JSONObject();
                res.put("sizeText", TripLogger.getStorageSizeText(ctx));
                res.put("trips", TripLogger.load(ctx));
                byte[] body = res.toString().getBytes(StandardCharsets.UTF_8);
                writeResponse(out, "200 OK", "application/json; charset=utf-8", body);
            } else if ("POST".equals(method) && path.startsWith("/api/trips/clear")) {
                int q = path.indexOf("tripId=");
                if (q >= 0) {
                    try {
                        long tid = Long.parseLong(path.substring(q + 7).trim());
                        TripLogger.clearTrip(ctx, tid);
                    } catch (Throwable ignored) {}
                } else {
                    TripLogger.clear(ctx);
                }
                writeResponse(out, "200 OK", "application/json; charset=utf-8", "{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
            } else if ("POST".equals(method) && "/api/data".equals(path)) {
                char[] buf = new char[contentLength];
                int read = 0;
                while (read < contentLength) {
                    int r = in.read(buf, read, contentLength - read);
                    if (r < 0) break;
                    read += r;
                }
                JSONObject obj = new JSONObject(new String(buf, 0, read));
                MaintenanceStore.save(ctx, obj);
                if (onDataChanged != null) onDataChanged.run();
                byte[] ok = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                writeResponse(out, "200 OK", "application/json; charset=utf-8", ok);
            } else {
                byte[] html = buildHtml().getBytes(StandardCharsets.UTF_8);
                writeResponse(out, "200 OK", "text/html; charset=utf-8", html);
            }
        } catch (Throwable ignored) {}
    }

    private void writeResponse(OutputStream out, String status, String contentType, byte[] body) throws Exception {
        String headers = "HTTP/1.1 " + status + "\r\n"
            + "Content-Type: " + contentType + "\r\n"
            + "Content-Length: " + body.length + "\r\n"
            + "Access-Control-Allow-Origin: *\r\n"
            + "Connection: close\r\n\r\n";
        out.write(headers.getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }

    public static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (!iface.isUp() || iface.isLoopback()) continue;
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Throwable ignored) {}
        return "127.0.0.1";
    }

    private String buildHtml() {
        return "<!DOCTYPE html><html lang='id'><head><meta charset='utf-8'/>"
            + "<meta name='viewport' content='width=device-width,initial-scale=1'/>"
            + "<title>KERE-HORE Moving Part</title>"
            + "<script src='https://cdn.tailwindcss.com'></script>"
            + "<script src='https://cdn.jsdelivr.net/npm/sweetalert2@11'></script>"
            + "<script src='https://cdn.jsdelivr.net/npm/chart.js'></script>"
            + "<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/>"
            + "<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>"
            + "<link rel='stylesheet' href='https://unpkg.com/leaflet.markercluster@1.5.3/dist/MarkerCluster.css'/>"
            + "<link rel='stylesheet' href='https://unpkg.com/leaflet.markercluster@1.5.3/dist/MarkerCluster.Default.css'/>"
            + "<script src='https://unpkg.com/leaflet.markercluster@1.5.3/dist/leaflet.markercluster.js'></script>"
            + "<link rel='stylesheet' href='https://api.mapbox.com/mapbox.js/plugins/leaflet-fullscreen/v1.0.1/leaflet.fullscreen.css'/>"
            + "<script src='https://api.mapbox.com/mapbox.js/plugins/leaflet-fullscreen/v1.0.1/Leaflet.fullscreen.min.js'></script>"
            + "<style>@media print{.no-print{display:none!important}body{background:#fff!important;color:#000!important}}</style>"
            + "</head><body class='bg-slate-950 text-slate-100 min-h-screen p-3 sm:p-5'>"
            + "<div class='max-w-2xl mx-auto space-y-4'>"
            + "<div class='flex items-center justify-between no-print'>"
            + "<div><h1 class='text-lg font-bold text-amber-400'>KERE-HORE Moving Part & GPS</h1>"
            + "<div class='flex items-center gap-2 mt-0.5'><span id='hdrLbl' class='px-2 py-0.5 rounded-md bg-amber-500/20 text-amber-300 text-[11px] font-semibold uppercase'></span>"
            + "<p class='text-xs text-slate-400'>Servis, Analitik SWOT, History & Riwayat Perjalanan</p></div></div>"
            + "<button onclick='window.print()' class='px-3 py-2 rounded-xl bg-rose-500 text-white text-xs font-bold'>Export PDF</button>"
            + "</div>"
            + "<div class='flex gap-2 no-print'>"
            + "<button onclick=\"showTab('parts')\" id='tParts' class='flex-1 py-2 rounded-xl bg-sky-500 text-slate-950 text-xs font-bold'>Servis & History</button>"
            + "<button onclick=\"showTab('analytics')\" id='tAna' class='flex-1 py-2 rounded-xl bg-slate-800 text-slate-300 text-xs font-bold'>Grafik & SWOT</button>"
            + "<button onclick=\"showTab('trips')\" id='tTrips' class='flex-1 py-2 rounded-xl bg-slate-800 text-slate-300 text-xs font-bold'>History GPS</button>"
            + "</div>"
            + "<div id='secParts' class='space-y-4'>"
            + "<div class='bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow-lg no-print'>"
            + "<div class='grid grid-cols-2 gap-3'>"
            + "<div><label class='text-xs text-slate-400'>KM Saat Ini</label>"
            + "<input id='curKm' type='number' inputmode='numeric' class='w-full mt-1 px-3 py-2.5 rounded-xl bg-slate-800 border border-slate-700 text-white text-sm'/></div>"
            + "<div><label class='text-xs text-slate-400'>Inisial Pojok Kiri Atas</label>"
            + "<input id='lbl' type='text' class='w-full mt-1 px-3 py-2.5 rounded-xl bg-slate-800 border border-slate-700 text-white text-sm'/></div>"
            + "</div>"
            + "<label class='flex items-center gap-2 mt-2.5 text-xs text-slate-300'>"
            + "<input id='autoSync' type='checkbox' class='rounded bg-slate-800 border-slate-700'/>"
            + "<span>Auto-Sync KM dari GPS Logger (otomatis bertambah saat berkendara)</span></label>"
            + "<button onclick='saveTop()' class='mt-3 w-full py-2.5 rounded-xl bg-sky-500 active:bg-sky-600 text-slate-950 font-semibold text-sm'>Simpan Odometer & Inisial</button>"
            + "</div>"
            + "<div class='bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow-lg'>"
            + "<div class='flex items-center justify-between mb-3'>"
            + "<h2 class='font-semibold text-slate-200 text-sm'>Daftar Moving Part</h2>"
            + "<button onclick='openModal(-1)' class='no-print px-3 py-1.5 rounded-xl bg-emerald-500 text-slate-950 text-xs font-bold'>+ Tambah Part</button>"
            + "</div>"
            + "<div id='list' class='space-y-2.5'></div>"
            + "</div>"
            + "<div class='bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow-lg'>"
            + "<h2 class='font-semibold text-slate-200 text-sm mb-2'>Riwayat Penggantian (History)</h2>"
            + "<div id='histList' class='space-y-2 text-xs text-slate-300'></div>"
            + "</div></div>"
            + "<div id='secAnalytics' class='hidden space-y-3'>"
            + "<div class='grid grid-cols-1 sm:grid-cols-2 gap-3'>"
            + "<div class='bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow-lg'>"
            + "<h2 class='font-semibold text-slate-200 text-sm mb-2'>Grafik Sisa KM Part</h2>"
            + "<canvas id='chartKm' height='200'></canvas></div>"
            + "<div class='bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow-lg'>"
            + "<h2 class='font-semibold text-slate-200 text-sm mb-2'>Spider Chart Kesehatan Servis</h2>"
            + "<canvas id='chartSwot' height='200'></canvas></div>"
            + "</div>"
            + "<details open class='bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow-lg'>"
            + "<summary class='cursor-pointer font-semibold text-sky-400 text-sm flex justify-between items-center'>"
            + "<span>1. Agregasi Perjalanan GPS (7H / 1B / 6B / 1T / Semua)</span><span id='sumAgg' class='text-xs text-slate-400'>-</span></summary>"
            + "<p class='text-xs text-slate-400 mt-2 mb-2'>Menghitung total jarak tempuh (Haversine) dan durasi berkendara dari log GPS.</p>"
            + "<div id='aggTable' class='overflow-x-auto text-xs'></div>"
            + "</details>"
            + "<details class='bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow-lg'>"
            + "<summary class='cursor-pointer font-semibold text-emerald-400 text-sm flex justify-between items-center'>"
            + "<span>2. Prediksi Umur Sparepart (Linear Burn-Rate)</span><span id='sumPred' class='text-xs text-slate-400'>-</span></summary>"
            + "<p class='text-xs text-slate-400 mt-2 mb-2'>Rumus: <code>Sisa KM ÷ Rata-rata KM/hari (30 hari)</code> untuk memprediksi estimasi hari penggantian.</p>"
            + "<div id='predList' class='space-y-1.5 text-xs'></div>"
            + "</details>"
            + "<details class='bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow-lg'>"
            + "<summary class='cursor-pointer font-semibold text-amber-400 text-sm flex justify-between items-center'>"
            + "<span>3. Analisis SWOT & Insight</span><span id='sumSwot' class='text-xs text-slate-400'>-</span></summary>"
            + "<div id='swotBox' class='grid grid-cols-1 sm:grid-cols-2 gap-2.5 text-xs mt-3'></div>"
            + "</details>"
            + "<details class='bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow-lg'>"
            + "<summary class='cursor-pointer font-semibold text-purple-400 text-sm flex justify-between items-center'>"
            + "<span>4. Pola & Efisiensi Berkendara</span><span id='sumDrive' class='text-xs text-slate-400'>-</span></summary>"
            + "<p class='text-xs text-slate-400 mt-2 mb-2'>Evaluasi kecepatan rata-rata, top speed, serta rasio waktu jalan vs parkir.</p>"
            + "<div id='driveBox' class='text-xs text-slate-300 space-y-1'></div>"
            + "</details>"
            + "</div>"
            + "<div id='secTrips' class='hidden space-y-4'>"
            + "<div class='bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow-lg'>"
            + "<div class='flex flex-wrap justify-between items-center gap-2 mb-3'>"
            + "<div><h2 class='font-semibold text-slate-200 text-sm'>Riwayat Pergerakan GPS</h2>"
            + "<p id='tripSize' class='text-xs text-slate-400'>Total Data: -</p></div>"
            + "<div class='flex gap-2'>"
            + "<button onclick='exportGpx()' class='px-2.5 py-1 rounded-lg bg-emerald-600/30 text-emerald-300 text-xs'>Export GPX</button>"
            + "<button onclick='clearThisTrip()' class='px-2.5 py-1 rounded-lg bg-amber-600/30 text-amber-300 text-xs'>Hapus Trip Ini</button>"
            + "<button onclick='clearTrips()' class='px-2.5 py-1 rounded-lg bg-rose-600/30 text-rose-300 text-xs'>Bersihkan</button>"
            + "</div></div>"
            + "<div class='flex flex-wrap gap-2 mb-3'>"
            + "<select id='tripSel' onchange='renderTripView()' class='flex-1 px-2.5 py-1.5 rounded-lg bg-slate-800 border border-slate-700 text-xs text-white'></select>"
            + "<select id='tripPerPageSel' onchange='tripPerPage=parseInt(this.value);setTripPage(1);' class='px-2 py-1.5 rounded-lg bg-slate-800 border border-slate-700 text-xs text-white'>"
            + "<option value='5' selected>5/hal</option><option value='10'>10/hal</option><option value='20'>20/hal</option><option value='50'>50/hal</option></select>"
            + "<button onclick='togglePlay()' id='btnPlay' class='px-3 py-1.5 rounded-lg bg-sky-500 text-slate-950 text-xs font-bold'>Play</button>"
            + "<button onclick='cycleSpeed()' id='btnSpd' class='px-2.5 py-1.5 rounded-lg bg-slate-800 text-slate-300 text-xs'>1x</button>"
            + "<button onclick='toggleCluster()' id='btnClus' class='px-2.5 py-1.5 rounded-lg bg-slate-800 text-slate-300 text-xs'>Cluster: OFF</button>"
            + "</div>"
            + "<div id='tripStats' class='text-xs text-slate-300 mb-2'></div>"
            + "<div id='tripMap' class='w-full h-64 rounded-xl mb-3 bg-slate-800'></div>"
            + "<div id='tripList' class='space-y-2 text-xs'></div>"
            + "<div id='tripPager' class='flex justify-between items-center mt-3 text-xs'></div>"
            + "</div></div></div>"
            + "<div id='modal' class='fixed inset-0 bg-black/75 backdrop-blur-sm hidden items-end sm:items-center justify-center p-0 sm:p-4 z-50'>"
            + "<div class='bg-slate-900 border-t sm:border border-slate-700 rounded-t-2xl sm:rounded-2xl w-full max-w-md p-4 space-y-3'>"
            + "<div class='flex justify-between items-center'><h3 id='mTitle' class='font-bold text-white'>Edit Part</h3>"
            + "<button onclick='closeModal()' class='text-slate-400 text-xl leading-none px-2'>&times;</button></div>"
            + "<input id='mIdx' type='hidden'/>"
            + "<div><label class='text-xs text-slate-400'>Nama Part</label><input id='mName' type='text' class='w-full mt-1 px-3 py-2 rounded-xl bg-slate-800 border border-slate-700 text-white text-sm'/></div>"
            + "<div class='grid grid-cols-2 gap-2.5'>"
            + "<div><label class='text-xs text-slate-400'>KM Terakhir</label><input id='mLast' type='number' inputmode='numeric' class='w-full mt-1 px-3 py-2 rounded-xl bg-slate-800 border border-slate-700 text-white text-sm'/></div>"
            + "<div><label class='text-xs text-slate-400'>Target KM</label><input id='mTarget' type='number' inputmode='numeric' class='w-full mt-1 px-3 py-2 rounded-xl bg-slate-800 border border-slate-700 text-white text-sm'/></div>"
            + "</div>"
            + "<div class='grid grid-cols-2 gap-2.5'>"
            + "<div><label class='text-xs text-slate-400'>Merk (opsional)</label><input id='mBrand' type='text' class='w-full mt-1 px-3 py-2 rounded-xl bg-slate-800 border border-slate-700 text-white text-sm'/></div>"
            + "<div><label class='text-xs text-slate-400'>Tipe (opsional)</label><input id='mType' type='text' class='w-full mt-1 px-3 py-2 rounded-xl bg-slate-800 border border-slate-700 text-white text-sm'/></div>"
            + "</div>"
            + "<div><label class='text-xs text-slate-400'>Harga Rp (opsional)</label><input id='mPrice' type='number' inputmode='numeric' class='w-full mt-1 px-3 py-2 rounded-xl bg-slate-800 border border-slate-700 text-white text-sm'/></div>"
            + "<div class='flex gap-2 pt-1'>"
            + "<button onclick='closeModal()' class='flex-1 py-2.5 rounded-xl bg-slate-800 text-slate-300 text-sm font-medium'>Batal</button>"
            + "<button onclick='saveModal()' class='flex-1 py-2.5 rounded-xl bg-sky-500 text-slate-950 text-sm font-bold'>Simpan</button>"
            + "</div></div></div>"
            + "<script>"
            + "let state={currentKm:0,customLabel:'ardith666',widgetEnabled:true,parts:[],history:[]};let chartObj=null,swotChart=null,tripMap=null,tripLayer=null;"
            + "const toast=(t)=>Swal.fire({toast:true,position:'top',icon:'success',title:t,showConfirmButton:false,timer:1500,background:'#0f172a',color:'#f8fafc'});"
            + "function showTab(t){['Parts','Analytics','Trips'].forEach(k=>{document.getElementById('sec'+k).classList.toggle('hidden',k.toLowerCase()!==t);});"
            + "const map={parts:'tParts',analytics:'tAna',trips:'tTrips'};"
            + "Object.keys(map).forEach(k=>{const b=document.getElementById(map[k]);if(b){b.className=k===t?'flex-1 py-2 rounded-xl bg-sky-500 text-slate-950 text-xs font-bold':'flex-1 py-2 rounded-xl bg-slate-800 text-slate-300 text-xs font-bold';}});"
            + "if(t==='analytics')renderAnalytics();if(t==='trips')loadTrips();}"
            + "async function load(){const r=await fetch('/api/data');state=await r.json();if(!state.history)state.history=[];render();}"
            + "let allTrips=[],playTimer=null,playIdx=0,playSpeed=1,playMarker=null,useCluster=false,isFs=false,tripPage=1,tripPerPage=5;"
            + "async function loadTrips(){const r=await fetch('/api/trips');const res=await r.json();allTrips=res.trips||[];"
            + "document.getElementById('tripSize').innerText='Total Data Tersimpan: '+(res.sizeText||'-');"
            + "const sel=document.getElementById('tripSel');const ids=[...new Set(allTrips.map(x=>x.tripId||0))];"
            + "sel.innerHTML='<option value=\"all\">Semua Titik ('+allTrips.length+')</option>';"
            + "ids.forEach((id,i)=>{const cnt=allTrips.filter(x=>(x.tripId||0)===id).length;sel.innerHTML+=`<option value='${id}'>Perjalanan #${i+1} (${cnt} titik)</option>`;});"
            + "renderTripView();}"
            + "function getFilteredTrips(){const v=document.getElementById('tripSel').value;return v==='all'?allTrips:allTrips.filter(x=>String(x.tripId||0)===v);}"
            + "function renderTripView(){stopPlay();const arr=getFilteredTrips();const el=document.getElementById('tripList');el.innerHTML='';"
            + "if(!tripMap){tripMap=L.map('tripMap',{fullscreenControl:true}).setView([-6.2,106.8],12);L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{attribution:'OSM'}).addTo(tripMap);tripLayer=L.layerGroup().addTo(tripMap);}"
            + "setTimeout(()=>tripMap.invalidateSize(),200);tripLayer.clearLayers();"
            + "if(!arr.length){document.getElementById('tripStats').innerText='';el.innerHTML='<p class=\"text-slate-400\">Belum ada catatan perjalanan GPS.</p>';return;}"
            + "let topSpd=0;arr.forEach(x=>{if((x.kmh||0)>topSpd)topSpd=x.kmh||0;});"
            + "document.getElementById('tripStats').innerText='Titik: '+arr.length+' | Top Speed: '+topSpd+' km/j';"
            + "const pts=arr.map(x=>[x.lat,x.lon]);L.polyline(pts,{color:'#38bdf8',weight:4}).addTo(tripLayer);"
            + "if(useCluster&&typeof L.markerClusterGroup==='function'){const cg=L.markerClusterGroup();arr.forEach(x=>cg.addLayer(L.marker([x.lat,x.lon]).bindPopup(x.ts+' • '+x.kmh+' km/j')));tripLayer.addLayer(cg);}"
            + "else{L.marker(pts[0]).bindPopup('Mulai: '+arr[0].ts).addTo(tripLayer);L.marker(pts[pts.length-1]).bindPopup('Akhir: '+(arr[arr.length-1].endTs||arr[arr.length-1].ts)).addTo(tripLayer);}"
            + "tripMap.fitBounds(L.latLngBounds(pts),{padding:[20,20]});"
            + "tripPage=1;renderTripPage(arr);}"
            + "function setTripPage(p){tripPage=p;renderTripPage(getFilteredTrips());}"
            + "function renderTripPage(arr){const el=document.getElementById('tripList');const pg=document.getElementById('tripPager');el.innerHTML='';pg.innerHTML='';"
            + "if(!arr.length)return;const rev=arr.slice().reverse();const tot=Math.ceil(rev.length/tripPerPage);"
            + "if(tripPage>tot)tripPage=tot;if(tripPage<1)tripPage=1;"
            + "const slice=rev.slice((tripPage-1)*tripPerPage,tripPage*tripPerPage);"
            + "slice.forEach(x=>{const p=x.park&&x.endTs&&x.endTs!==x.ts?' (Parkir s/d '+x.endTs+')':'';"
            + "el.innerHTML+=`<div class='p-2.5 rounded-xl bg-slate-800/70 flex justify-between items-center'>"
            + "<div><div class='font-semibold text-slate-200'>${x.ts}${p} • ${x.kmh} km/j</div><div class='text-slate-400'>${x.lat}, ${x.lon}</div></div>"
            + "<a target='_blank' href='https://maps.google.com/?q=${x.lat},${x.lon}' class='px-2.5 py-1 rounded-lg bg-sky-500/20 text-sky-300'>Maps</a></div>`;});"
            + "if(tot>1){pg.innerHTML=`<button onclick='setTripPage(${tripPage-1})' ${tripPage<=1?'disabled':''} class='px-3 py-1 rounded-lg bg-slate-800 disabled:opacity-40'>Prev</button>"
            + "<span class='text-slate-400'>Hal ${tripPage} / ${tot} (${rev.length} titik)</span>"
            + "<button onclick='setTripPage(${tripPage+1})' ${tripPage>=tot?'disabled':''} class='px-3 py-1 rounded-lg bg-slate-800 disabled:opacity-40'>Next</button>`;}}"
            + "function toggleCluster(){useCluster=!useCluster;document.getElementById('btnClus').innerText='Cluster: '+(useCluster?'ON':'OFF');renderTripView();}"
            + "function toggleFs(){isFs=!isFs;const m=document.getElementById('tripMap');if(isFs){m.className='fixed inset-0 z-50 w-screen h-screen bg-slate-900';document.getElementById('btnFs').innerText='Exit Fullscreen';}else{m.className='w-full h-64 rounded-xl mb-3 bg-slate-800';document.getElementById('btnFs').innerText='Fullscreen';}if(tripMap)setTimeout(()=>tripMap.invalidateSize(),200);}"
            + "function cycleSpeed(){playSpeed=playSpeed===1?2:(playSpeed===2?5:1);document.getElementById('btnSpd').innerText=playSpeed+'x';}"
            + "function stopPlay(){if(playTimer){clearInterval(playTimer);playTimer=null;}document.getElementById('btnPlay').innerText='Play';}"
            + "function togglePlay(){if(playTimer){stopPlay();return;}const arr=getFilteredTrips();if(!arr.length)return;"
            + "playIdx=0;document.getElementById('btnPlay').innerText='Pause';"
            + "if(!playMarker)playMarker=L.circleMarker([arr[0].lat,arr[0].lon],{radius:8,color:'#fbbf24',fillColor:'#fbbf24',fillOpacity:1}).addTo(tripLayer);"
            + "playTimer=setInterval(()=>{if(playIdx>=arr.length){stopPlay();return;}const x=arr[playIdx++];"
            + "playMarker.setLatLng([x.lat,x.lon]).bindPopup(x.ts+' • '+x.kmh+' km/j').openPopup();},Math.round(600/playSpeed));}"
            + "function exportGpx(){const arr=getFilteredTrips();if(!arr.length)return;"
            + "let gpx='<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\"><trk><name>KERE-HORE Trip</name><trkseg>';"
            + "arr.forEach(x=>{gpx+=`<trkpt lat=\"${x.lat}\" lon=\"${x.lon}\"><time>${x.ts.replace(' ','T')}Z</time></trkpt>`;});"
            + "gpx+='</trkseg></trk></gpx>';const a=document.createElement('a');a.href=URL.createObjectURL(new Blob([gpx],{type:'application/gpx+xml'}));a.download='kere-hore-trip.gpx';a.click();}"
            + "function clearThisTrip(){const v=document.getElementById('tripSel').value;if(v==='all'){Swal.fire({icon:'info',title:'Pilih salah satu perjalanan dulu',background:'#0f172a',color:'#f8fafc'});return;}"
            + "confirm10s('Hapus Perjalanan Ini?','/api/trips/clear?tripId='+v,'Trip dihapus');}"
            + "function clearTrips(){confirm10s('Hapus Semua History GPS?','/api/trips/clear','History GPS dibersihkan');}"
            + "function confirm10s(title,url,doneMsg){let sec=10,iv=null;Swal.fire({title,html:'<b>PERINGATAN:</b> Data yang dihapus <b>tidak dapat dikembalikan</b>!<br><br>Tunggu <b id=\"cd\">10</b> detik...',icon:'warning',showCancelButton:true,confirmButtonColor:'#e11d48',cancelButtonColor:'#334155',confirmButtonText:'Hapus (10)',cancelButtonText:'Batal',background:'#0f172a',color:'#f8fafc',didOpen:()=>{const btn=Swal.getConfirmButton();btn.disabled=true;iv=setInterval(()=>{sec--;const el=document.getElementById('cd');if(el)el.innerText=sec;if(sec<=0){clearInterval(iv);btn.disabled=false;btn.innerText='Ya, Hapus Permanen';}else{btn.innerText='Hapus ('+sec+')';}},1000);},willClose:()=>{if(iv)clearInterval(iv);}}).then(async r=>{if(r.isConfirmed){await fetch(url,{method:'POST'});loadTrips();toast(doneMsg);}});}"
            + "function hav(a,b,c,d){const R=6371,dLat=(c-a)*Math.PI/180,dLon=(d-b)*Math.PI/180,x=Math.sin(dLat/2)**2+Math.cos(a*Math.PI/180)*Math.cos(c*Math.PI/180)*Math.sin(dLon/2)**2;return R*2*Math.atan2(Math.sqrt(x),Math.sqrt(1-x));}"
            + "async function renderAnalytics(){const ctx=document.getElementById('chartKm').getContext('2d');"
            + "const labels=(state.parts||[]).map(p=>p.name);const data=(state.parts||[]).map(p=>effTarget(p)-(state.currentKm||0));"
            + "if(chartObj)chartObj.destroy();chartObj=new Chart(ctx,{type:'bar',data:{labels,datasets:[{label:'Sisa KM',data,backgroundColor:data.map(v=>v<=0?'#fb7185':(v<=1000?'#fbbf24':'#34d399'))}]},options:{plugins:{legend:{display:false}},scales:{y:{ticks:{color:'#94a3b8'}},x:{ticks:{color:'#94a3b8'}}}}});"
            + "const totalP=(state.parts||[]).length||1;"
            + "const okP=(state.parts||[]).filter(p=>(effTarget(p)-(state.currentKm||0))>1000).length;"
            + "const healthScore=Math.round((okP/totalP)*100);"
            + "const dataScore=Math.round(((state.parts||[]).filter(p=>p.brand||p.price).length/totalP)*100);"
            + "const histScore=Math.min(100,(state.history||[]).length*20);"
            + "const ctxS=document.getElementById('chartSwot').getContext('2d');"
            + "if(swotChart)swotChart.destroy();swotChart=new Chart(ctxS,{type:'radar',data:{labels:['Kesehatan Part','Kepatuhan Jadwal','Kelengkapan Data','Riwayat Servis'],datasets:[{label:'Skor (%)',data:[healthScore,Math.max(20,healthScore),Math.max(25,dataScore),Math.max(20,histScore)],backgroundColor:'rgba(56,189,248,0.2)',borderColor:'#38bdf8',pointBackgroundColor:'#38bdf8'}]},options:{scales:{r:{min:0,max:100,ticks:{display:false},grid:{color:'#334155'},pointLabels:{color:'#cbd5e1',font:{size:10}}}}}});"
            + "const crit=(state.parts||[]).filter(p=>(effTarget(p)-(state.currentKm||0))<=0).map(p=>p.name);"
            + "const warn=(state.parts||[]).filter(p=>{const r=effTarget(p)-(state.currentKm||0);return r>0&&r<=1000;}).map(p=>p.name);"
            + "const totCost=(state.history||[]).reduce((s,h)=>s+(h.price||0),0);"
            + "document.getElementById('swotBox').innerHTML="
            + "`<div class='p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/30'><b class='text-emerald-400'>Strengths</b><p class='mt-1 text-slate-300'>${(state.parts||[]).length} komponen terpantau aktif. Total ${(state.history||[]).length} riwayat servis tercatat.</p></div>"
            + "<div class='p-3 rounded-xl bg-amber-500/10 border border-amber-500/30'><b class='text-amber-400'>Weaknesses</b><p class='mt-1 text-slate-300'>${warn.length?'Mendekati jadwal: '+warn.join(', '):'Tidak ada part mendekati batas.'}</p></div>"
            + "<div class='p-3 rounded-xl bg-sky-500/10 border border-sky-500/30'><b class='text-sky-400'>Opportunities</b><p class='mt-1 text-slate-300'>Estimasi pengeluaran tercatat: Rp${totCost.toLocaleString('id-ID')}. Jadwalkan servis gabungan agar hemat biaya.</p></div>"
            + "<div class='p-3 rounded-xl bg-rose-500/10 border border-rose-500/30'><b class='text-rose-400'>Threats</b><p class='mt-1 text-slate-300'>${crit.length?'SEGERA GANTI: '+crit.join(', '):'Semua komponen masih dalam batas aman.'}</p></div>`;"
            + "document.getElementById('sumSwot').innerText=crit.length?crit.length+' Kritis':'Semua Aman';"
            + "try{const tr=await fetch('/api/trips');const tres=await tr.json();const tarr=tres.trips||[];const now=Date.now();"
            + "const periods=[['7 Hari',7],['1 Bulan',30],['6 Bulan',180],['1 Tahun',365],['Semua',99999]];"
            + "let tbl='<table class=\"w-full text-left\"><thead><tr class=\"text-slate-400 border-b border-slate-800\"><th class=\"py-1\">Periode</th><th>Jarak</th><th>Durasi</th></tr></thead><tbody>';"
            + "let km30=0,totSpd=0,cntSpd=0,maxSpd=0,parkCnt=0;"
            + "periods.forEach(([nm,days])=>{const cut=now-days*86400000;const sub=tarr.filter(x=>new Date((x.ts||'').replace(' ','T')).getTime()>=cut);"
            + "let km=0,ms=0;for(let i=1;i<sub.length;i++){if(sub[i].tripId===sub[i-1].tripId){km+=hav(sub[i-1].lat,sub[i-1].lon,sub[i].lat,sub[i].lon);ms+=Math.max(0,new Date((sub[i].ts||'').replace(' ','T')).getTime()-new Date((sub[i-1].ts||'').replace(' ','T')).getTime());}}"
            + "if(days===30)km30=km;if(days===7)document.getElementById('sumAgg').innerText=km.toFixed(1)+' km / 7 hari';"
            + "tbl+=`<tr class='border-b border-slate-800/50'><td class='py-1.5'>${nm}</td><td>${km.toFixed(1)} km</td><td>${(ms/3600000).toFixed(1)} jam</td></tr>`;});"
            + "tbl+='</tbody></table>';document.getElementById('aggTable').innerHTML=tbl;"
            + "const kmPerDay=km30>0?(km30/30):30;"
            + "let pHtml='';(state.parts||[]).forEach(p=>{const rem=effTarget(p)-(state.currentKm||0);const d=Math.max(0,Math.round(rem/kmPerDay));"
            + "pHtml+=`<div class='flex justify-between p-2 rounded-lg bg-slate-800/60'><span>${p.name}</span><span class='text-emerald-300'>~${d} hari lagi (${fmt(rem)} km)</span></div>`;});"
            + "document.getElementById('predList').innerHTML=pHtml;document.getElementById('sumPred').innerText='Basis '+kmPerDay.toFixed(1)+' km/hari';"
            + "tarr.forEach(x=>{if(x.park)parkCnt++;if((x.kmh||0)>0){totSpd+=x.kmh;cntSpd++;if(x.kmh>maxSpd)maxSpd=x.kmh;}});"
            + "const avgSpd=cntSpd?Math.round(totSpd/cntSpd):0;"
            + "document.getElementById('sumDrive').innerText='Avg '+avgSpd+' km/j | Top '+maxSpd+' km/j';"
            + "document.getElementById('driveBox').innerHTML=`<div>• Kecepatan Rata-rata: <b>${avgSpd} km/j</b></div><div>• Top Speed Tercatat: <b>${maxSpd} km/j</b></div><div>• Titik Jalan vs Parkir: <b>${tarr.length-parkCnt}</b> jalan / <b>${parkCnt}</b> parkir</div>`;"
            + "}catch(e){}}"
            + "async function push(msg){await fetch('/api/data',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(state)});render();if(msg)toast(msg);}"
            + "function saveTop(){const oldKm=state.currentKm||0;const newKm=parseInt(document.getElementById('curKm').value||0);"
            + "state.currentKm=newKm;state.customLabel=document.getElementById('lbl').value||'ardith666';state.autoSyncKm=document.getElementById('autoSync').checked;"
            + "if(oldKm===0&&newKm>0){(state.parts||[]).forEach(p=>{if((p.lastKm||0)===0){const iv=p.targetKm>0?p.targetKm:5000;p.lastKm=newKm;p.targetKm=newKm+iv;}});}"
            + "push('Odometer & Inisial disimpan');}"
            + "function effTarget(p){const l=p.lastKm||0,t=p.targetKm||0;return(t>0&&t<l)?(l+t):t;}"
            + "function fmt(n){return Number(n||0).toLocaleString('id-ID');}"
            + "function render(){document.getElementById('curKm').value=state.currentKm||0;document.getElementById('lbl').value=state.customLabel||'ardith666';document.getElementById('autoSync').checked=(state.autoSyncKm!==false);"
            + "const hl=document.getElementById('hdrLbl');if(hl)hl.innerText=state.customLabel||'ardith666';"
            + "const el=document.getElementById('list');el.innerHTML='';"
            + "(state.parts||[]).forEach((p,i)=>{const tgt=effTarget(p);const rem=tgt-(state.currentKm||0);"
            + "const badge=rem<=0?'bg-rose-500/20 text-rose-400':(rem<=1000?'bg-amber-500/20 text-amber-400':'bg-emerald-500/20 text-emerald-400');"
            + "el.innerHTML+=`<div class='p-3 rounded-xl bg-slate-800/70 border border-slate-700/60 space-y-1.5'>"
            + "<div class='flex justify-between items-center'><span class='font-semibold text-sm'>${p.name}</span>"
            + "<span class='text-xs px-2 py-0.5 rounded-full ${badge}'>Sisa ${fmt(rem)} km</span></div>"
            + "<div class='text-xs text-slate-400'>Terakhir: ${fmt(p.lastKm||0)} km | Target: ${fmt(tgt)} km</div>"
            + "<div class='text-xs text-slate-400'>${p.brand?p.brand+' ':''}${p.type||''} ${p.price?'• Rp'+fmt(p.price):''}</div>"
            + "<div class='flex gap-2 pt-1'>"
            + "<button onclick='openModal(${i})' class='px-3 py-1.5 rounded-lg bg-slate-700 text-xs font-medium'>Edit</button>"
            + "<button onclick='delPart(${i})' class='px-3 py-1.5 rounded-lg bg-rose-600/30 text-rose-300 text-xs font-medium'>Hapus</button>"
            + "</div></div>`;});}"
            + "function openModal(i){document.getElementById('mIdx').value=i;const m=document.getElementById('modal');"
            + "if(i>=0){const p=state.parts[i];document.getElementById('mTitle').innerText='Edit Moving Part';"
            + "document.getElementById('mName').value=p.name||'';document.getElementById('mLast').value=p.lastKm||0;"
            + "document.getElementById('mTarget').value=p.targetKm||0;document.getElementById('mBrand').value=p.brand||'';"
            + "document.getElementById('mType').value=p.type||'';document.getElementById('mPrice').value=p.price||0;"
            + "}else{const cur=state.currentKm||0;document.getElementById('mTitle').innerText='Tambah Moving Part';"
            + "document.getElementById('mName').value='';document.getElementById('mLast').value=cur;"
            + "document.getElementById('mTarget').value=cur+5000;document.getElementById('mBrand').value='';"
            + "document.getElementById('mType').value='';document.getElementById('mPrice').value=0;}"
            + "m.classList.remove('hidden');m.classList.add('flex');}"
            + "function closeModal(){const m=document.getElementById('modal');m.classList.add('hidden');m.classList.remove('flex');}"
            + "function saveModal(){const i=parseInt(document.getElementById('mIdx').value);"
            + "const name=document.getElementById('mName').value.trim();"
            + "if(!name){Swal.fire({icon:'warning',title:'Nama part wajib diisi',background:'#0f172a',color:'#f8fafc'});return;}"
            + "const obj={id:i>=0?state.parts[i].id:Date.now()+'',name,lastKm:parseInt(document.getElementById('mLast').value||0),"
            + "targetKm:parseInt(document.getElementById('mTarget').value||0),brand:document.getElementById('mBrand').value.trim(),"
            + "type:document.getElementById('mType').value.trim(),price:parseInt(document.getElementById('mPrice').value||0),date:'',notes:''};"
            + "if(i>=0)state.parts[i]=obj;else state.parts.push(obj);closeModal();push('Part disimpan');}"
            + "function delPart(i){Swal.fire({title:'Hapus part ini?',icon:'warning',showCancelButton:true,confirmButtonColor:'#e11d48',cancelButtonColor:'#334155',confirmButtonText:'Hapus',cancelButtonText:'Batal',background:'#0f172a',color:'#f8fafc'}).then(r=>{if(r.isConfirmed){state.parts.splice(i,1);push('Part dihapus');}});}"
            + "load();</script></body></html>";
    }
}
