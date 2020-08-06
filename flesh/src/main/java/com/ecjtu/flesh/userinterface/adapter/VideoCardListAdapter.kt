package com.ecjtu.flesh.userinterface.adapter

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.BitmapImageViewTarget
import com.bumptech.glide.request.target.Target
import com.ecjtu.componentes.activity.RotateNoCreateActivity
import com.ecjtu.flesh.R
import com.ecjtu.flesh.db.DatabaseManager
import com.ecjtu.flesh.db.table.impl.ClassPageTableImpl
import com.ecjtu.flesh.db.table.impl.HistoryTableImpl
import com.ecjtu.flesh.db.table.impl.LikeTableImpl
import com.ecjtu.flesh.model.models.VideoModel
import com.ecjtu.flesh.userinterface.fragment.IjkVideoFragment
import com.ecjtu.netcore.model.PageModel
import com.ecjtu.netcore.network.AsyncNetwork
import com.ecjtu.netcore.network.IRequestCallback
import org.json.JSONArray
import org.json.JSONObject
import tv.danmaku.ijk.media.exo.video.IjkVideoView
import java.net.HttpURLConnection

/**
 * Created by Ethan_Xiang on 2018/1/16.
 */
open class VideoCardListAdapter(var pageModel: List<VideoModel>, private val recyclerView: RecyclerView, private val url: String? = null) : RecyclerViewWrapAdapter<VideoCardListAdapter.VH>(), RequestListener<Bitmap>, View.OnClickListener, IChangeTab {

    private var mDatabase: SQLiteDatabase? = null

    private var mLastClickPosition = -1

    private val linearLayoutManager: LinearLayoutManager? = if (recyclerView.layoutManager is LinearLayoutManager) recyclerView.layoutManager as LinearLayoutManager? else null

    private var mIsInForeground = true

    private var mPlayViewHolder: VH? = null

    override fun getItemCount(): Int {
        return pageModel.size
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        if (mLastClickPosition < linearLayoutManager?.findFirstVisibleItemPosition() ?: 0 ||
                mLastClickPosition > linearLayoutManager?.findLastVisibleItemPosition() ?: 0) {
            if (mLastClickPosition >= 0) {
                mPlayViewHolder?.ijkVideoView?.apply {
                    pause()
                    this.mediaController?.hide()
                    Log.i("VideoCardListAdapter", "pause 1 video position " + mLastClickPosition)
                }
            }
        }

        val context = holder?.itemView?.context
        val model = pageModel.get(position)
        holder?.textView?.text = model.title

        //db
        if (mDatabase == null || mDatabase?.isOpen == false) {
            mDatabase?.close()
            val manager = DatabaseManager.getInstance(context)
            mDatabase = manager?.getDatabase()
        }
        val href = pageModel[position].videoUrl
        if (mDatabase != null && mDatabase?.isOpen == true) {
            val impl = LikeTableImpl()
            holder?.heart?.isActivated = impl.isLike(mDatabase!!, href)
        }

        if (!mIsInForeground) {
            mPlayViewHolder?.ijkVideoView?.pause()
            holder?.ijkVideoView?.pause()
            holder?.thumb?.visibility = View.VISIBLE
            Log.i("VideoCardListAdapter", "pause video 2 position " + mLastClickPosition)
        }

        if (mLastClickPosition != position) {
            holder?.ijkVideoView?.pause()
            holder?.thumb?.visibility = View.VISIBLE
            Log.i("VideoCardListAdapter", "pause 3 video position " + mLastClickPosition)
        } else {
            holder?.thumb?.visibility = View.INVISIBLE
        }

        val videoUrl = model.videoUrl
        holder?.itemView?.setTag(R.id.extra_tag_2, videoUrl)
        holder?.itemView?.setTag(R.id.extra_tag_3, holder)
        holder?.itemView?.setOnClickListener(this)
        holder?.itemView?.setTag(R.id.extra_tag, position)

        holder?.heart?.setTag(R.id.extra_tag, videoUrl)


        val imageView = holder?.thumb
        val options = RequestOptions()
        options.centerCrop()
        val url = pageModel.get(position).imageUrl /*thumb2OriginalUrl(pageModel.itemList[position].imgUrl)*/
        var host = ""
        if (url.startsWith("https://")) {
            host = url.replace("https://", "")
        } else if (url.startsWith("http://")) {
            host = url.replace("http://", "")
        }

        host = host.substring(0, if (host.indexOf("/") >= 0) host.indexOf("/") else host.length)
        val builder = LazyHeaders.Builder()
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 5.1.1; Nexus 6 Build/LYZ28E)  Chrome/60.0.3112.90 Mobile Safari/537.36")
                .addHeader("Accept", "image/webp,image/apng,image/*,*/*;q=0.8")
                .addHeader("Accept-Encoding", "gzip, deflate")
                .addHeader("Accept-Language", "zh-CN,zh;q=0.8")
                .addHeader("Host", host)
                .addHeader("Proxy-Connection", "keep-alive")
                .addHeader("Referer", "http://m.mzitu.com/")
        if (!TextUtils.isEmpty(url)) {
            val glideUrl = GlideUrl(url, builder.build())
            url.let {
                Glide.with(context).asBitmap().load(glideUrl).listener(this).apply(options).into(imageView)
            }
        }
        imageView?.setTag(R.id.extra_tag, position)
        holder?.textView?.setText(pageModel.get(position).title)
        val bottom = holder?.itemView?.findViewById<View>(R.id.bottom)
        bottom?.visibility = View.VISIBLE

        if (position == itemCount - 1) {
            requestMore(context, itemCount, 50)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent?.context).inflate(R.layout.layout_card_view, parent, false)
        return VH(v)
    }

    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Bitmap>?, isFirstResource: Boolean): Boolean {
        if (target is BitmapImageViewTarget) {
            var parent: View = target.view
            while (true) {
                if (parent.id == R.id.container) {
                    break
                }
                parent = parent.parent as View
            }
            val bottom = parent.findViewById<View>(R.id.bottom)
            bottom.visibility = View.VISIBLE
        }
        return false
    }

    override fun onResourceReady(resource: Bitmap?, model: Any?, target: Target<Bitmap>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
        if (target is BitmapImageViewTarget) {
            var parent: View = target.view
            while (true) {
                if (parent.id == R.id.container) {
                    break
                }
                parent = parent.parent as View
            }
            val layoutParams = (parent as View).layoutParams
            var height = resource?.height ?: LinearLayout.LayoutParams.WRAP_CONTENT

            val bottom = parent.findViewById<View>(R.id.bottom)
            height += bottom.height
            if (layoutParams.height != height) {
                layoutParams.height = height
            }
            val position = target.view.getTag(R.id.extra_tag) as Int
            target.view.setImageBitmap(resource)
            setHeight(position, height)
            bottom.visibility = View.VISIBLE
        }
        return true
    }

    override fun getItemViewType(position: Int): Int {
        return super.getItemViewType(position)
    }

    override fun onClick(v: View?) {
        val position = v?.getTag(R.id.extra_tag) as Int?
        val isInSamePos = mLastClickPosition == position
//        position?.let {
//            val lastPos = mLastClickPosition
//            mLastClickPosition = position
//            if (!isInSamePos) {
//                mPlayViewHolder?.ijkVideoView?.pause()
//                Log.i("VideoCardListAdapter", "pause 5 video position " + lastPos)
//            }
//        }
        val videoUrl = v?.getTag(R.id.extra_tag_2) as String?
        val context = v?.context
        if (videoUrl != null && context != null) {
            val db = DatabaseManager.getInstance(v.context)?.getDatabase() as SQLiteDatabase
            val impl = HistoryTableImpl()
            impl.addHistory(db, videoUrl)
            db.close()
            val intent = RotateNoCreateActivity.newInstance(context, IjkVideoFragment::class.java
                    , Bundle().apply { putString(IjkVideoFragment.EXTRA_URI_PATH, videoUrl.toString()) })
            context.startActivity(intent)
        }

//        videoUrl?.let {
//            val videoView = v?.findViewById(R.id.ijk_video) as IjkVideoView
//            val thumb = v.findViewById(R.id.thumb) as ImageView?
//            (videoView.mediaController as SimpleMediaController?)?.updatePausePlay()
//            if (videoView.isPlaying) {
//                thumb?.visibility = View.INVISIBLE
//                return@let
//            }
//            mPlayViewHolder = v.getTag(R.id.extra_tag_3) as VH?
//            thumb?.visibility = View.INVISIBLE
//            if (isInSamePos && videoView.isInPlaybackState) {
//                videoView.start()
//                Log.i("VideoCardListAdapter", "start 1 video position " + mLastClickPosition)
//            } else {
//                videoView.setVideoPath(videoUrl)
//                videoView.start()
//                Log.i("VideoCardListAdapter", "start 2 video position " + mLastClickPosition)
//            }
//            (videoView.mediaController as SimpleMediaController?)?.updatePausePlay()
//        }
    }

    open fun onRelease() {
        mDatabase?.close()
        mPlayViewHolder?.ijkVideoView?.apply {
            release(true)
            Log.i("VideoCardListAdapter", "release video position " + mLastClickPosition)
        }
    }

    open fun onResume() {
        mIsInForeground = true
        if (mLastClickPosition >= 0) {
            notifyItemChanged(mLastClickPosition)
        }
        mLastClickPosition = -1
    }

    open fun onStop() {
        mIsInForeground = false
        mPlayViewHolder?.ijkVideoView?.pause()
    }

    open fun onDestroy() {
        onRelease()
    }

    override fun onSelectTab() {
    }

    override fun onUnSelectTab() {
        onRelease()
    }

    open fun getHeartClickListener() = { v: View? ->
        val manager = DatabaseManager.getInstance(v?.context)
        val db = manager?.getDatabase() as SQLiteDatabase
        val url = v?.getTag(R.id.extra_tag) as String?
        if (url != null) {
            val impl = LikeTableImpl()
            if (impl.isLike(db, url)) {
                impl.deleteLike(db, url)
                v?.isActivated = false
            } else {
                impl.addLike(db, url)
                v?.isActivated = true
            }
        }
        db.close()
    }

    open fun getDatabase(): SQLiteDatabase? {
        return mDatabase
    }

    open fun requestMore(context: Context?, index: Int, length: Int) {
        if (TextUtils.isEmpty(url)) {
            return
        }
        AsyncNetwork().request(url + "&index=$index&length=$length")
                .setRequestCallback(object : IRequestCallback {
                    override fun onSuccess(httpURLConnection: HttpURLConnection?, response: String) {
                        try {
                            val pageList = pageModel as ArrayList
                            val serverData = JSONObject(response)
                            val jObj = JSONArray(serverData.optString("data"))
                            for (i in 0 until jObj.length()) {
                                val jTitle = jObj[i] as JSONObject
                                val title = jTitle.optString("title")
                                val list = jTitle.optJSONArray("list")
                                val modelList = arrayListOf<VideoModel>()
                                for (j in 0 until list.length()) {
                                    val v33Model = VideoModel()
                                    val jItem = list[j] as JSONObject
                                    v33Model.baseUrl = jItem.optString("baseUrl")
                                    v33Model.imageUrl = jItem.optString("imageUrl")
                                    v33Model.title = jItem.optString("title")
                                    v33Model.videoUrl = jItem.optString("videoUrl")
                                    modelList.add(v33Model)
                                }

                                for (model in modelList) {
                                    if (pageList.indexOf(model) < 0) {
                                        pageList.add(model)
                                    }
                                }

                                if (context != null) {
                                    val impl = ClassPageTableImpl()
                                    val db = DatabaseManager.getInstance(context)?.getDatabase()
                                    val itemListModel = arrayListOf<PageModel.ItemModel>()
                                    for (videoModel in modelList) {
                                        val model = PageModel.ItemModel(videoModel.videoUrl, videoModel.title, videoModel.imageUrl, 1)
                                        itemListModel.add(model)
                                    }
                                    val pageModel = PageModel(itemListModel)
                                    pageModel.nextPage = ""
                                    db?.let {
                                        db.beginTransaction()
                                        impl.addPage(db, pageModel)
                                        db.setTransactionSuccessful()
                                        db.endTransaction()
                                    }
                                    db?.close()
                                }
                                recyclerView.post {
                                    notifyItemChanged(index)
                                }
                            }
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        }
                    }
                })
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ijkVideoView: IjkVideoView? = /*itemView.findViewById(R.id.ijk_video) as IjkVideoView?*/null
        val textView = itemView.findViewById<View>(R.id.title) as TextView
        val heart = itemView.findViewById<View>(R.id.heart) as ImageView
        val description = itemView.findViewById<View>(R.id.description) as TextView
//        val thumb = itemView.findViewById(R.id.thumb) as ImageView
//        val mediaController = AndroidMediaController(itemView.context)

        val thumb = itemView.findViewById<View>(R.id.image) as ImageView

        init {
            heart.setOnClickListener(this@VideoCardListAdapter.getHeartClickListener())
//            ijkVideoView?.setMediaController(mediaController)
//            ijkVideoView?.setOnInfoListener { mp, what, extra ->
//                if (what == IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
//                }
//                return@setOnInfoListener false
//            }
//            mediaController.setMediaPlayerCallback {
//                val videoUrl = itemView.getTag(R.id.extra_tag_2) as String?
//                val intent = RotateNoCreateActivity.newInstance(itemView.context, IjkVideoFragment::class.java
//                        , Bundle().apply { putString(IjkVideoFragment.EXTRA_URI_PATH, videoUrl) })
//                itemView.context.startActivity(intent)
//            }
        }
    }
}