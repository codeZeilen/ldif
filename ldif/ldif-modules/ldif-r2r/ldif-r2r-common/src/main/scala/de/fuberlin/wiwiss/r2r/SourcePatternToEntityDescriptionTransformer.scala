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

package de.fuberlin.wiwiss.r2r

import de.fuberlin.wiwiss.r2r.parser._
import de.fuberlin.wiwiss.r2r.parser.NodeType._
import ldif.entity.{EntityDescription, Path, Restriction, PathOperator}
import ldif.entity.Restriction._
import ldif.entity.{BackwardOperator, ForwardOperator}
import ldif.util.Uri
import scala.collection.JavaConversions._
import java.util.HashMap
import collection.immutable.List._
import ldif.entity

/**
 * Created by IntelliJ IDEA.
 * User: andreas
 * Date: 03.05.11
 * Time: 19:23
 * To change this template use File | Settings | File Templates.
 */


object SourcePatternToEntityDescriptionTransformer {

  def printEntityDescription(entityDescription: EntityDescription) {
    println("Paths:")
    for(pattern <- entityDescription.patterns(0)) {
      println("  " + pattern)
    }
    println("Restrictions:")
    println("  " + entityDescription.restriction)
  }

  def transform(sourcePattern: String, variableDependencies: Set[String], prefixMapper: PrefixMapper): (EntityDescription, Map[String, Int]) = {
    val triples: List[NodeTriple] =  Sparql2NodeTripleParser.parse(sourcePattern, prefixMapper).toList
    val (paths, operators) = constructPathsAndRestrictions(triples, variableDependencies)
    var index = 0
    var variableToIndexMap: Map[String, Int] = Map()
    var pattern: List[Path] = List()
    for((variableValue ,path) <- paths) {
      variableToIndexMap += (variableValue -> index)
      index += 1
      pattern = path :: pattern
    }
    var restriction = Restriction(None)
    if(operators.size>0)
      restriction = Restriction(Some(And(operators)))
    // reverse 'pattern' because new paths were inserted at the head
    (EntityDescription(restriction, IndexedSeq(pattern.toIndexedSeq.reverse)), variableToIndexMap)
  }

  def constructPathsAndRestrictions(triples: List[NodeTriple], variableDependencies: Set[String]): (Map[String, Path],
      List[Operator]) = {
    var paths: Map[String, Path] = Map()
    var restrictions: List[Operator] = List()

    val subjRoot = constructTree(triples)
    if(subjRoot==null) throw new R2RException("No SUBJ variable in Source Pattern")

    // Construct the entity description tree starting at ?SUBJ node
    def recursiveConstruct(node: TreeNode, path: List[PathOperator], visited: Set[TreeNode]) {
      val parsedNode = node.getNode
      val nodeType = parsedNode.nodeType
      var added = false
      if(nodeType==VARIABLENODE && variableDependencies.contains(parsedNode.value) && parsedNode.value!="SUBJ") {
        paths += (parsedNode.value -> Path("SUBJ", path))
        added = true
      }

      val links = node.getLinks
      val linked = hasLinks(links, visited)

      if(!linked) {
        if((nodeType==VARIABLENODE && !added) || nodeType==BLANKNODE)
          restrictions = Exists(Path("SUBJ",path)) :: restrictions
        else if(!added) {
          var node: entity.Node = null
          if(parsedNode.nodeType==URINODE)
            node = entity.Node.createUriNode(parsedNode.value, "")
          else if(parsedNode.nodeType==TYPEDLITERAL)
            node = entity.Node.createTypedLiteral(parsedNode.value, parsedNode.datatype(), "")
          else if(parsedNode.nodeType==LANGUAGELITERAL)
            node = entity.Node.createLanguageLiteral(parsedNode.value, parsedNode.language, "")
          else
            node = entity.Node.createLiteral(parsedNode.value, "")
          restrictions = Condition(Path("SUBJ",path), Set(node)) :: restrictions
        }
      } else {
        links.foreach{case (propertyNode, nextNode, backward) =>
          if(!visited.contains(nextNode)) {

            val visitedNew = visited + nextNode

            if(backward)
              recursiveConstruct(nextNode, path ++ List(BackwardOperator(propertyNode.value)), visitedNew)
            else
              recursiveConstruct(nextNode, path ++ List(ForwardOperator(propertyNode.value)), visitedNew)
          }
        }
      }
    }

    def hasLinks(links: List[(Node, TreeNode, Boolean)], visited: Set[TreeNode]): Boolean = {
      var count = 0
      links.foreach{case (propertyNode, nextNode, backward) =>
        if(!visited.contains(nextNode))
          count += 1
      }
      count > 0
    }

    recursiveConstruct(subjRoot, List(), Set(subjRoot))
    (paths, restrictions)
  }

  class TreeNode(node: Node) {
    private var links: List[(Node, TreeNode, Boolean)] = List()

    def setLink(propertyNode: Node, node: TreeNode, backward: Boolean): Unit = {
      links = (propertyNode, node, backward) :: links
    }

    def getNode = node
    def getLinks = links
  }

  /**
   * returns the tree starting at the SUBJ node
   */
  private def constructTree(triples: List[NodeTriple]): TreeNode = {
    val nodeTable = new HashMap[Node, TreeNode]
    triples.foreach(triple => processTriple(nodeTable, triple))
    nodeTable.get(Node.createVariableNode("SUBJ"))
  }

  private def processTriple(nodeTable: HashMap[Node, TreeNode], triple: NodeTriple) {
    def fetchTreeNode(node: Node) = {
      var treeNode: TreeNode = nodeTable.get(node)
      if(treeNode==null) {
        treeNode = new TreeNode(node)
        nodeTable.put(node, treeNode)
      }
      treeNode
    }

    def makeTreeNodesWithLinks: Unit = {
      val s = triple.getSubject
      val p = triple.getPredicate
      val o = triple.getObject

      val sNode = fetchTreeNode(s)
      val oNode = fetchTreeNode(o)

      if (s.nodeType() == BLANKNODE || s.nodeType == URINODE || s.nodeType == VARIABLENODE)
        sNode.setLink(p, oNode, backward = false)

      if (o.nodeType() == BLANKNODE || o.nodeType == URINODE || o.nodeType == VARIABLENODE)
        oNode.setLink(p, sNode, backward = true)
    }

    makeTreeNodesWithLinks
  }
}


