package com.vadhara7.podplay.ui

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.method.ScrollingMovementMethod
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.vadhara7.podplay.R
import com.vadhara7.podplay.adapter.EpisodeListAdapter
import com.vadhara7.podplay.service.PodplayMediaService
import com.vadhara7.podplay.viewmodel.PodcastViewModel
import kotlinx.android.synthetic.main.fragment_podcast_details.*

class PodcastDetailsFragment : Fragment(), EpisodeListAdapter.EpisodeListAdapterListener {
    private var listener: OnPodcastDetailsListener? = null
    private lateinit var podcastViewModel: PodcastViewModel
    private lateinit var episodeListAdapter: EpisodeListAdapter
    private var menuItem: MenuItem? = null
    private lateinit var mediaBrowser: MediaBrowserCompat
    private var mediaControllerCallback: MediaControllerCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        setupViewModel()
        initMediaBrowser()
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater!!.inflate(R.layout.fragment_podcast_details, container, false)
    }
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setupControls()
        updateControls()
    }

    override fun onSelectedEpisode(episodeViewData: PodcastViewModel.EpisodeViewData) {
        var controller = MediaControllerCompat.getMediaController(activity!!)
        if (controller.playbackState != null) {
            if (controller.playbackState.state ==
                PlaybackStateCompat.STATE_PLAYING) {
                controller.transportControls.pause()
            } else {
                startPlaying(episodeViewData)
            }
        } else {
            startPlaying(episodeViewData)
        }
    }
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater?.inflate(R.menu.menu_details, menu)
        menuItem = menu?.findItem(R.id.menu_feed_action)
        updateMenuItem()
    }

    private fun setupViewModel() {
        podcastViewModel = ViewModelProviders.of(activity!!).get(PodcastViewModel::class.java)
    }

    private fun updateControls() {
        val viewData = podcastViewModel.activePodcastViewData ?: return
        feedTitleTextView.text = viewData.feedTitle
        feedDescTextView.text = viewData.feedDesc
        Glide.with(activity).load(viewData.imageUrl).into(feedImageView)
    }

    private fun setupControls() {
        feedDescTextView.movementMethod = ScrollingMovementMethod()
        episodeRecyclerView.setHasFixedSize(true)
        val layoutManager = LinearLayoutManager(activity)
        episodeRecyclerView.layoutManager = layoutManager
        val dividerItemDecoration = androidx.recyclerview.widget.DividerItemDecoration(episodeRecyclerView.context, layoutManager.orientation)
        episodeRecyclerView.addItemDecoration(dividerItemDecoration)
        episodeListAdapter = EpisodeListAdapter(podcastViewModel.activePodcastViewData?.episodes, this)
        episodeRecyclerView.adapter = episodeListAdapter
    }

    companion object {
        fun newInstance(): PodcastDetailsFragment {
            return PodcastDetailsFragment()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_feed_action -> {
                podcastViewModel.activePodcastViewData?.feedUrl?.let {
                    if (podcastViewModel.activePodcastViewData?.subscribed!!) {
                        listener?.onUnsubscribe()
                    } else {
                        listener?.onSubscribe()
                    }
                }
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    interface OnPodcastDetailsListener {

        fun onSubscribe()
        fun onUnsubscribe()
    }
    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnPodcastDetailsListener) {
            listener = context
        } else {
            throw RuntimeException(context!!.toString() + " must implement OnPodcastDetailsListener")
        }
    }

    private fun updateMenuItem() {
        val viewData = podcastViewModel.activePodcastViewData ?: return
        menuItem?.title = if (viewData.subscribed)
            getString(R.string.unsubscribe) else getString(R.string.subscribe)
    }

    inner class MediaControllerCallback: MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            println("metadata changed to ${metadata?.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI)}")
        }
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            println("state changed to $state")
        }
    }

    private fun registerMediaController(token: MediaSessionCompat.Token) {

        val mediaController = MediaControllerCompat(activity, token)
        MediaControllerCompat.setMediaController(activity!!, mediaController)
        mediaControllerCallback = MediaControllerCallback()
        mediaController.registerCallback(mediaControllerCallback!!)
    }

    inner class MediaBrowserCallBacks:
        MediaBrowserCompat.ConnectionCallback() {

        override fun onConnected() {
            super.onConnected()
            registerMediaController(mediaBrowser.sessionToken)
            println("onConnected")
        }
        override fun onConnectionSuspended() {
            super.onConnectionSuspended()
            println("onConnectionSuspended")
// Disable transport controls
        }
        override fun onConnectionFailed() {
            super.onConnectionFailed()
            println("onConnectionFailed")
// Fatal error handling
        }
    }

    private fun initMediaBrowser() {
        mediaBrowser = MediaBrowserCompat(activity,
            ComponentName(activity!!, PodplayMediaService::class.java),
            MediaBrowserCallBacks(),
            null)
    }

    override fun onStart() {
        super.onStart()
        if (mediaBrowser.isConnected) {
            if (MediaControllerCompat.getMediaController(activity!!) == null) {
                registerMediaController(mediaBrowser.sessionToken)
            }
        } else {
            mediaBrowser.connect()
        }
    }

    override fun onStop() {
        super.onStop()
        if (MediaControllerCompat.getMediaController(activity!!) != null) {
            mediaControllerCallback?.let {
                MediaControllerCompat.getMediaController(activity!!)
                    .unregisterCallback(it)
            }
        }
    }

    private fun startPlaying(episodeViewData: PodcastViewModel.EpisodeViewData) {
        val controller = MediaControllerCompat.getMediaController(activity!!)
        val viewData = podcastViewModel.activePodcastViewData ?: return
        val bundle = Bundle()
        bundle.putString(MediaMetadataCompat.METADATA_KEY_TITLE, episodeViewData.title)
        bundle.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, viewData.feedTitle)
        bundle.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, viewData.imageUrl)
        controller.transportControls.playFromUri(Uri.parse(episodeViewData.mediaUrl), bundle)
    }
}
