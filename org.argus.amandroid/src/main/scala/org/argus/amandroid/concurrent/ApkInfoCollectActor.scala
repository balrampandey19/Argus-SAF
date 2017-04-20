/*
 * Copyright (c) 2017. Fengguo Wei and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Detailed contributors are listed in the CONTRIBUTOR.md
 */

package org.argus.amandroid.concurrent

import akka.actor._
import org.argus.amandroid.concurrent.util.GlobalUtil
import org.argus.amandroid.core.ApkGlobal
import org.argus.amandroid.core.appInfo.AppInfoCollector
import org.argus.amandroid.core.model.ApkModel
import org.argus.amandroid.serialization.stage.Staging
import org.argus.jawa.core.{MsgLevel, PrintReporter}

class ApkInfoCollectActor extends Actor with ActorLogging {
  def receive: Receive = {
    case acd: ApkInfoCollectData =>
      sender ! collectInfo(acd)
  }
  
  private def collectInfo(acdata: ApkInfoCollectData): ApkInfoCollectResult = {
    log.info("Start collect info for " + acdata.fileUri)
    val srcs = acdata.srcFolders
    val outApkUri = acdata.outApkUri
    val reporter = new PrintReporter(MsgLevel.ERROR)
    val apk = new ApkGlobal(ApkModel(acdata.fileUri, outApkUri, srcs), reporter)
    try {
      GlobalUtil.buildGlobal(apk, outApkUri, srcs)
      AppInfoCollector.collectInfo(apk)
      Staging.stageApkModel(apk.model, outApkUri)
      ApkInfoCollectSuccResult(apk.model, acdata.outApkUri, acdata.srcFolders)
    } catch {
      case e: Exception =>
        ApkInfoCollectFailResult(apk.model.nameUri, e)
    }
  }
}
