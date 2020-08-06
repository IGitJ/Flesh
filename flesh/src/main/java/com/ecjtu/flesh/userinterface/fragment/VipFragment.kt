package com.ecjtu.flesh.userinterface.fragment

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.preference.PreferenceManager
import android.telephony.TelephonyManager
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.amazonaws.ClientConfiguration
import com.amazonaws.Protocol
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.Bucket
import com.ecjtu.flesh.Constants
import com.ecjtu.flesh.model.models.S3BucketModel
import com.ecjtu.flesh.userinterface.adapter.VipTabPagerAdapter
import com.ecjtu.flesh.userinterface.dialog.GetVipDialogHelper
import com.ecjtu.flesh.util.encrypt.SecretKeyUtils
import com.ecjtu.netcore.model.MenuModel
import com.ecjtu.netcore.network.AsyncNetwork
import com.ecjtu.netcore.network.BaseNetwork
import com.ecjtu.netcore.network.IRequestCallback
import com.ecjtu.netcore.network.IRequestCallbackV2
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.util.*
import kotlin.collections.ArrayList


/**
 * Created by KerriGan on 2018/2/21.
 */
class VipFragment : VideoListFragment() {

    private var mS3: AmazonS3Client? = null
    private var mBuckets: List<Bucket>? = null
    private var mRequest: BaseNetwork? = null
    private var mAccessible = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        getToolbar().setTitle("Vip")
    }

    override fun onUserVisibleHintChanged(isVisibleToUser: Boolean) {
        super.onUserVisibleHintChanged(isVisibleToUser)
        if (isVisibleToUser) {
            val telephonyManager = context!!.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager?
            val deviceId = telephonyManager?.getDeviceId()
            val serverUrl = PreferenceManager.getDefaultSharedPreferences(getContext()).getString(Constants.PREF_SERVER_URL, Constants.SERVER_URL)
            AsyncNetwork().request(serverUrl + "/api/getUserByDeviceId?deviceId=" + deviceId)
                    .setRequestCallback(object : IRequestCallbackV2 {
                        override fun onSuccess(httpURLConnection: HttpURLConnection?, response: String) {
                            try {
                                val jObj = JSONObject(response)
                                val code = (jObj.opt("code") as String).toInt()
                                if (code >= 0) {
                                    requestQuestion()
                                } else {
                                    getHandler().post {
                                        getVipInfo()
                                    }
                                }
                            } catch (ex: Exception) {
                                ex.printStackTrace()
                            }
                        }

                        override fun onError(httpURLConnection: HttpURLConnection?, exception: java.lang.Exception) {
                            exception.printStackTrace()
                            if (activity != null) {
                                activity?.finish()
                            }
                        }
                    })

        }
    }

    private fun requestQuestion() {
        AsyncNetwork().apply {
            request(Constants.QUESTION).setRequestCallback(object : IRequestCallback {
                override fun onSuccess(httpURLConnection: HttpURLConnection?, response: String) {
                    val jArr = JSONArray(response)
                    try {
                        val jObj = jArr.get(Random(System.currentTimeMillis()).nextInt(jArr.length())) as JSONObject
                        val question = jObj.optString("question")
                        val answer1 = jObj.optString("answer1")
                        val answer2 = jObj.optString("answer2")
                        val answer3 = jObj.optString("answer3")
                        val answer = Integer.valueOf(jObj.getString("answer"))
                        if (!mAccessible) {
                            val listener = { dialog: DialogInterface, which: Int ->
                                if (which == DialogInterface.BUTTON_POSITIVE) {
                                } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                                } else if (which == DialogInterface.BUTTON_NEUTRAL) {
                                }
                                if (which == answer) {
                                    mAccessible = true
                                    doLoading()
                                } else {
                                    if (activity != null) {
                                        activity?.finish()
                                    }
                                }
                            }
                            if (activity != null) {
                                activity?.runOnUiThread {
                                    if (activity == null)
                                        return@runOnUiThread
                                    AlertDialog.Builder(context!!).setTitle("回答问题才能进入").setMessage(question).setPositiveButton(answer1, listener).setNegativeButton(answer2, listener).setNeutralButton(answer3, listener).setOnCancelListener { if (!mAccessible && activity != null) activity?.finish() }.create().show()
                                }
                            }
                        }
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            })
        }
    }

    private fun doLoading() {
        if (mS3 == null) {
            mRequest = AsyncNetwork().request(Constants.S3BUCKET_MAPPING).apply {
                setRequestCallback(object : IRequestCallback {
                    override fun onSuccess(httpURLConnection: HttpURLConnection?, response: String) {
                        var index = 0
                        do {
                            try {
                                val bucketList = S3BucketModel.fromJson(response)
                                val secretKey = SecretKeyUtils.getKeyFromServer()
                                val content = SecretKeyUtils.getS3InfoFromServer(secretKey!!.key)
                                val params = content.split(",")
                                val provider = BasicAWSCredentials(params[0], params[1])
                                val config = ClientConfiguration()
                                if (index == 0) {
                                    config.protocol = Protocol.HTTP
                                } else {
                                    config.protocol = Protocol.HTTPS
                                }
                                mS3 = AmazonS3Client(provider, config)
                                val region = Region.getRegion(Regions.CN_NORTH_1)
                                mS3?.setRegion(region)
                                mS3?.setEndpoint(Constants.S3_URL)
                                val buckets = mS3?.listBuckets()
                                mBuckets = buckets
                                val menuModel = mutableListOf<MenuModel>()
                                if (bucketList != null) {
                                    val renameBucket = ArrayList<Bucket>()
                                    for (bucket in bucketList) {
                                        val menu = MenuModel(bucket.title, "")
                                        menuModel.add(menu)
                                        var i = 0
                                        while (i < mBuckets?.size ?: 0) {
                                            if (bucket.bucketName.equals(mBuckets?.get(i)?.name)) {
                                                if (mBuckets?.get(i) != null) {
                                                    renameBucket.add(mBuckets?.get(i)!!)
                                                }
                                                break
                                            } else if (i == mBuckets?.size ?: 0 - 1) {
                                            }
                                            i++
                                        }
                                    }
                                    mBuckets = renameBucket

                                    if (activity != null && Thread.currentThread().isInterrupted == false) {
                                        activity?.runOnUiThread {
                                            if (mRequest == null) {
                                                return@runOnUiThread
                                            }
                                            if (getViewPager() != null && getViewPager()?.adapter == null) {
                                                getViewPager()?.adapter = VipTabPagerAdapter(menuModel, getViewPager()!!)
                                                (getViewPager()?.adapter as VipTabPagerAdapter?)?.setBucketsList(mBuckets!!, mS3!!, config.protocol)
                                                (getViewPager()?.adapter as VipTabPagerAdapter?)?.notifyDataSetChanged(true)
                                            } else {
                                                (getViewPager()?.adapter as VipTabPagerAdapter).menu = menuModel
                                                (getViewPager()?.adapter as VipTabPagerAdapter?)?.setBucketsList(mBuckets!!, mS3!!, config.protocol)
                                                (getViewPager()?.adapter as VipTabPagerAdapter?)?.notifyDataSetChanged(false)
                                            }
                                            if (userVisibleHint) {
                                                attachTabLayout()
                                            }
                                        }
                                    }
                                }
                                break
                            } catch (e: Exception) {
                                e.printStackTrace()
                                if (index >= 1) {
                                    break
                                }
                                index++
                            }
                        } while (!Thread.interrupted())
                    }
                })
            }

        }
    }

    private fun getVipInfo() {
        if (context != null) {
            GetVipDialogHelper(context!!).getDialog()?.show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mRequest?.cancel()
        mRequest = null
    }
}