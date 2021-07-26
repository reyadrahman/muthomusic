package com.mutho.music.ui.queue

import android.content.Context
import android.view.MenuItem
import com.mutho.music.tagger.TaggerDialog
import com.mutho.music.ui.dialog.DeleteDialog
import io.reactivex.Single

interface QueueContract {

    interface View {

        fun setData(queueItems: List<QueueItem>, position: Int)

        fun updateQueuePosition(queuePosition: Int)

        fun showToast(message: String, duration: Int)

        fun showTaggerDialog(taggerDialog: TaggerDialog)

        fun showDeleteDialog(deleteDialog: DeleteDialog)

        fun onRemovedFromQueue(queueItem: QueueItem)

        fun onRemovedFromQueue(queueItems: List<QueueItem>)
    }

    interface Presenter {

        fun saveQueue(context: Context)

        fun saveQueue(context: Context, item: MenuItem)

        fun clearQueue()

        fun removeFromQueue(queueItem: QueueItem)

        fun removeFromQueue(queueItems: Single<List<QueueItem>>)

        fun loadData()

        fun onQueueItemClick(queueItem: QueueItem)
    }
}