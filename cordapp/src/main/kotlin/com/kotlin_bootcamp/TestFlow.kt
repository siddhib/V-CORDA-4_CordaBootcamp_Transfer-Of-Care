package com.kotlin_bootcamp

//import co.paralleluniverse.fibers.Suspendable
//import net.corda.client.rpc.CordaRPCClient
//import net.corda.core.crypto.SecureHash
//import net.corda.core.flows.FlowLogic
//import net.corda.core.messaging.CordaRPCOps
//import net.corda.core.utilities.NetworkHostAndPort
//import net.corda.core.utilities.ProgressTracker
//import java.io.File
//
//class TestFlow(val host: String,
//               val port: Int,
//               val jar_path: String): FlowLogic<Unit>() {
//
//
//    override val progressTracker: ProgressTracker? = ProgressTracker()
//    @Suspendable
//    override fun call(): Unit {
//        val rpcConnection = CordaRPCClient(NetworkHostAndPort(host,port)).start("user1", "test")
//        val proxy = rpcConnection.proxy
//
//        val attachmentHash = uploadAttachment(proxy, jar_path)
//        println("Blacklist uploaded to node at $host:$port")
//        println("attachment Hash $attachmentHash")
//    }
//}
//
//private fun uploadAttachment(proxy: CordaRPCOps, attachmentPath: String): SecureHash {
//    val attachmentUploadInputStream = File(attachmentPath).inputStream()
//    return proxy.uploadAttachment(attachmentUploadInputStream)
//}