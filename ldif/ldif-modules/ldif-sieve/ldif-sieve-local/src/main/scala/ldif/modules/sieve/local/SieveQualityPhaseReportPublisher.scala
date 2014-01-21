/*
 * LDIF
 *
 * Copyright 2011-2014 Universität Mannheim, MediaEvent Services GmbH & Co. KG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ldif.modules.sieve.local

import collection.mutable.ArrayBuffer
import ldif.util.{ReportItem, Report, JobDetailsStatusMonitor}
import java.util.concurrent.atomic.AtomicInteger


/**
 * Created by IntelliJ IDEA.
 * User: andreas
 * Date: 3/8/12
 * Time: 6:12 PM
 * To change this template use File | Settings | File Templates.
 */

class SieveQualityPhaseReportPublisher extends JobDetailsStatusMonitor("Sieve Quality Assessment")       {

  var entitiesTotal = 0
  var entitiesProcessed = new AtomicInteger(0)

  override def getReport: Report = {
    var customReportItems = new ArrayBuffer[ReportItem]
    if(entitiesTotal>0)
      customReportItems = customReportItems :+ ReportItem.get("Entities processed",entitiesProcessed +"/"+entitiesTotal)
    super.getReport(customReportItems)
  }

  private def getProgress : String =
   if (entitiesTotal>0)
    (entitiesProcessed.get*100/entitiesTotal).toInt + "%"
   else "Running..."

  override def getStatus : Option[String] =  status.orElse(Some(getProgress))
}