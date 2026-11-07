package com.bqsqwad.share.domain

data class Peer(
    val endpointId: String,
    val name: String,
    val trusted: Boolean = false,
    val stableIdentity: String? = null,
)

enum class Direction { SEND, RECEIVE }

enum class TransferStatus { QUEUED, RUNNING, PAUSED, DONE, FAILED, BLOCKED }

data class TransferItem(
    val id: Long = 0,
    val direction: Direction,
    val displayName: String,
    val mime: String,
    val bytes: Long,
    val bytesDone: Long,
    val status: TransferStatus,
    val peerName: String,
    val localPath: String?,
    val createdAt: Long,
    val favorite: Boolean = false,
)

data class ShareOffer(
    val endpointId: String,
    val peerName: String,
    val fileCount: Int,
    val totalBytes: Long,
    val verification: String,
)
