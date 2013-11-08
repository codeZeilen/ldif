package ldif.modules.sieve.fusion.functions

/*
 * Copyright 2011-2013 Freie Universität Berlin, MediaEvent Services GmbH & Co. KG
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

import org.slf4j.LoggerFactory
import ldif.entity.NodeTrait
import ldif.modules.sieve.fusion.FusionFunction
import ldif.modules.sieve.quality.QualityAssessmentProvider
import ldif.util.Prefixes
import collection.mutable.HashMap

/**
 * Fusion function that keeps the most common value amongst sources.
 * Each source has one votes an amount proportional to its score according to a given quality metric.
 *
 * @author pablomendes
 */

class WeightedVoting(metricId: String) extends FusionFunction(metricId) {

  private val log = LoggerFactory.getLogger(getClass.getName)

  /**
   * Picks the value with the highest quality assessment with one pass over all nodes in all patterns in input.
   */
  override def fuse(patterns: Traversable[IndexedSeq[NodeTrait]], quality: QualityAssessmentProvider) : Traversable[IndexedSeq[NodeTrait]] = {
    var bestValue = IndexedSeq[NodeTrait]()
    var votes = new HashMap[String,Double]()
    if (patterns.nonEmpty) {
      bestValue = patterns.head
      var maxVotes = 0.0
      patterns.foreach( nodes =>
        nodes.foreach( n =>{
          val score = quality.getScore(metricId, n.graph)
          val nVotes = votes.getOrElse(n.value,0.0)+score
          votes.put(n.value, nVotes)
          if (nVotes > maxVotes) {
            maxVotes = nVotes
            bestValue = IndexedSeq(n)
          }
      }))
    }

    Traversable(bestValue)
  }

}

object WeightedVoting {

  def fromXML(node: scala.xml.Node)(implicit prefixes: Prefixes) : FusionFunction = {
    val metricId = prefixes.resolve((node \ "@metric").text)
    new WeightedVoting(metricId)
  }
}