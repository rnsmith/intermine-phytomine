<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib tagdir="/WEB-INF/tags" prefix="im" %>

<!-- queryBuilderConstraint.jsp -->

<script type="text/javascript">
	var addedConstraint = 0;
</script>
<div id="constraint" style="display:none">
    <html:form action="/queryBuilderConstraintAction" styleId="queryBuilderConstraintForm">
    <c:choose>
      <c:when test="${!empty dec.path.pathString}">
        <html:hidden property="path" value="${dec.path.pathString}"/>
      </c:when>
      <c:otherwise>
        <html:hidden property="path" value="${dec.path}"/>
      </c:otherwise>
    </c:choose>
    <html:hidden property="editingConstraintCode" value="${dec.code}"/>
	
<c:if test="${(EDITING_TEMPLATE != null || NEW_TEMPLATE != null) && editingTemplateConstraint}">
  <c:set var="constraint" value="${dec}" scope="request"/>
  <tiles:insert page="constraintSettings.jsp"/>
</c:if>
<c:if test="${(EDITING_TEMPLATE != null || NEW_TEMPLATE != null) && editingSwitchableConstraint}">
  <c:set var="constraint" value="${dec}" scope="request"/>
  <tiles:insert page="constraintSwitchableSettings.jsp"/>
</c:if>

<%--to prevent submit twice --%>
<html:hidden
property="<%=org.apache.struts.taglib.html.Constants.TOKEN_KEY%>"
value="<bean:write name='<%=org.apache.struts.Globals.TRANSACTION_TOKEN_KEY%>'/>"/>

<c:if test="${!editingTemplateConstraint && !editingSwitchableConstraint}">
  <div class="heading constraintTitle">
    <fmt:message key="query.constrain"/><%--Constraint--%> 
  </div>
</c:if>

<c:if test="${!editingTemplateConstraint && !editingSwitchableConstraint}">
<div class="body">
 <c:choose>
 <c:when  test="${empty joinStyleOnly}" >
  <h3><fmt:message key="query.constraintHeading"/></h3> <%--1. Choose a filter--%>

<!--  
   ATTRIBUTE OR LOOKUP CONSTRAINT 
-->  
  <c:if test="${dec.path.attribute || dec.lookup}">
  <FIELDSET>
    <LEGEND>
    <a href="javascript:swapInputs('attribute');"> 
    <fmt:message key="query.filterValue" /><%--Filter query results on this field having a specific value.--%>
    </a>
    </LEGEND>
    <!--
   		ATTRIBUTE CONSTRAINT
    -->  	
    <c:if test="${dec.path.attribute}">
      <!-- field name -->
      <c:choose>
        <c:when test="${empty dec.path.fieldName}">
          <span class="type"> <c:out value="${dec.path.pathString}" /> </span>
        </c:when>
        <c:otherwise>
          <span class="attributeField"> <c:out value="${dec.path.fieldName}" /> </span>
        </c:otherwise>
      </c:choose>
      <c:choose>
        <c:when test="${dec.path.collection}">
          <fmt:message key="query.collection">
            <%--collection--%>
            <fmt:param value="${dec.path.type}" />
          </fmt:message>
        </c:when>
      </c:choose>
    </c:if>

    <!-- input or radio buttons -->
     <c:set var="selectedValue" value="" />
       <c:if test="${dec.valueSelected}"> <%--the constraint is not a bag or a nullconstraint --%>
         <c:set var="selectedValue" value="${dec.selectedValue}" />
       </c:if>

        <table border="0" cellspacing="2" cellpadding="1" border="0" class="noborder">
          <tr>
            <!--  constraint op -->
            <c:choose>
              <c:when test="${dec.path.type == 'Boolean'}">
                <!--  boolean doesn't have a separate op dropdown -->
                <td valign="top"><html:hidden property="attributeOp" styleId="attribute1"
                  value="0" disabled="false" /> 
                  <input type="radio" name="attributeValue" id="attribute2" value="true" 
                  <c:if test="${selectedValue == 'true'}">checked</c:if>/>
                  <fmt:message key="query.constraint.true" /> 
                  <input type="radio" name="attributeValue" id="attribute3" value="false"
                  <c:if test="${selectedValue == 'false'}">checked</c:if>/>
                  <fmt:message key="query.constraint.false" /> 
                  <input type="radio" name="attributeValue" id="attribute4" value="NULL"
                  <c:if test="${selectedValue == 'NULL'}">checked</c:if>/>
                  <fmt:message key="query.constraint.null" />
                </td><br/><br/>
              </c:when>
              <c:otherwise>
                <!-- dropdown to select constraint op or label for lookup-->
                <td valign="top">
                <c:choose>
	                <c:when test="${dec.path.attribute}">
	                  <html:select property="attributeOp" styleId="attribute5"
	                      onchange="onChangeAttributeOp();">
	                    <c:forEach items="${dec.validOps}" var="op">
	                      <option value="${op.property}"
	                        <c:if test="${!empty dec.selectedOp && dec.selectedOp.property == op.property}">selected</c:if>>
	                        <im:displayableOpName opName="${op.label}" valueType="${op.property}" />
	                      </option>
	                    </c:forEach>
	                  </html:select>
	                  </c:when>
                  <c:otherwise>
                   <html:hidden property="attributeOp" styleId="attribute5" value="${dec.lookupOp.property}" disabled="false" />
                   <fmt:message key="query.lookupConstraintLabel" /><%-- LOOKUP: --%>
                  </c:otherwise>
                 </c:choose>
                </td>
              
            
              <!-- constraint value -->
            <td valign="top" align="center">
              <c:if test="${!empty dec.possibleValues}">
                <c:set var="possibleValuesDisplay" value="display:none;"/>
                <c:if test="${dec.possibleValuesDisplayed}">
                  <c:set var="possibleValuesDisplay" value="display:inline;"/>
                </c:if>
                <html:select property="attributeOptions" styleId="attribute7" 
                    style="${possibleValuesDisplay}; padding-right: 10px"
                    onchange="this.form.attributeValue.value=this.value;"> 
                  <c:forEach items="${dec.possibleValues}" var="option">
                    <option value="${option}" <c:if test="${dec.selectedValue == option}">selected</c:if>>
                      <c:out value="${option}" />
                    </option>
                  </c:forEach>
                </html:select>
                
                <html:hidden property="multiValueAttribute" styleId="multiValueAttribute" value=""/>
                <c:set var="multiValuesDisplay" value="display:none;"/>
                <c:if test="${dec.multiValuesDisplayed}">
                  <c:set var="multiValuesDisplay" value="display:inline;"/>
                </c:if>
                <select id="multiValue" multiple size="4" onchange="updateMValueAttribute();" 
                style="${multiValuesDisplay};padding-right: 10px;">
                  <c:forEach items="${dec.possibleValues}" var="multiValue">
                    <option value="${multiValue}"
                      <c:if test="${fn:contains(dec.multiValuesAsString,multiValue)}">selected</c:if>>
                      <c:out value="${multiValue}"/>
                    </option>
                  </c:forEach>
                </select>
              </c:if>
              <c:choose>
                <%-- inputfield for an autocompletion --%>
                  <c:when test="${!empty dec.autoCompleter}">
                    <input name="attributeValue" id="attribute6" size="55"
                      style="background: #ffffc8" value="${dec.selectedValue}"
                      onKeyDown="getId(this.id); isSubmit(event);"
                      onKeyUp="readInput(event, '${dec.path.type}', '${dec.path.fieldName}');"
                      onMouseOver="setMouseOver(1);" onMouseOut="setMouseOver(0);"
                      onBlur="if(MOUSE_OVER != 1) { removeList(); }" />
                    <iframe width="100%" height="0" id="attribute6_IEbugFixFrame" marginheight="0"
                      marginwidth="0" frameborder="0" style="position: absolute;"> </iframe>
                    <div class="auto_complete" id="attribute6_display"
                      onMouseOver="setMouseOver(1);" onMouseOut="setMouseOver(0);">
                      </div>
                    <div class="error_auto_complete" id="attribute6_error"></div>
                  </c:when>
                  <%-- normal inputfield --%>
                  <c:otherwise>
                    <im:dateInput attributeType="${dec.path.type}" property="attributeValue" 
                        styleId="attribute8" value="${(dec.possibleValuesDisplayed && dec.selectedValue == null) ? dec.possibleValues[0] : dec.selectedValue}" 
                        onkeypress="if(event.keyCode == 13) {$('attribute').click();return false;}"
                        visible="${dec.inputFieldDisplayed}"/>
                  </c:otherwise>
                </c:choose> 
                </td>
            </c:otherwise>
            </c:choose>
            <td valign="top">&nbsp; <html:submit property="attribute" styleId="attributeSubmit"
              disabled="false">
              <fmt:message key="query.submitConstraint" />
              <%--Add to query--%>
            </html:submit></td>
          </tr>
        </table>
        <c:choose>
          <c:when test="${dec.lookup && dec.extraConstraint}">
            <p style="text-align: left;"><fmt:message key="bagBuild.extraConstraint">
              <fmt:param value="${dec.extraConstraintClassName}" />
            </fmt:message> <html:select property="extraValue" styleId="extraValue1" value="${dec.selectedExtraValue}">
              <html:option value="">Any</html:option>
              <!-- this should set to extraValue if editing existing constraint -->
              <c:forEach items="${dec.extraConstraintValues}" var="value">
                <html:option value="${value}">
                  <c:out value="${value}" />
                </html:option>
              </c:forEach>
            </html:select></p>
          </c:when>
          <c:otherwise>
            <html:hidden property="extraValue" value="" />
          </c:otherwise>
        </c:choose>
  
	<!--  
	   BAGS CONSTRAINT 
	--> 
	  <br />
	  <c:if test="${!dec.path.attribute && !empty dec.bags}">
	    <strong><fmt:message key="query.or" /></strong>
	    <input type="checkbox" id="checkBoxBag" onclick="swapInputs('bag');" />
	     <%--
		<html:checkbox property="useBagConstraint" styleId="bag1" disabled="true"/>
	      --%>
	    <fmt:message key="query.bagConstraint" /><%--Contained in bag:--%>
	    <html:select property="bagOp" styleId="bag1" disabled="true">
	      <c:forEach items="${dec.bagOps}" var="bagOp">
	        <html:option value="${bagOp.property}">
	          <c:out value="${bagOp.label}" />
	        </html:option>
	      </c:forEach>
	    </html:select> 
	    <html:select property="bagValue" styleId="bag2" disabled="true">
	      <c:forEach items="${dec.bags}" var="bag">
	        <option value="${bag}" <c:if test="${dec.bagSelected && dec.selectedValue == bag}">selected</c:if>>
	          <c:out value="${bag}" />
	        </option>
	      </c:forEach>
	    </html:select> 
	<br/><br/>
	</c:if> 
	</FIELDSET>
	</c:if> 

 
<!--
   SUBCLASSES  CONSTRAINT
--> 
 <c:if test="${dec.path.indentation != 0 && !empty SUBCLASSES[dec.path.type]}">
	<!-- SUBCLASS -->
    <FIELDSET>
    <LEGEND>
    <a href="javascript:swapInputs('subclass');"> <fmt:message key="query.filterSubclass" /><%--Filter query results based on this field being a member of a specific class of objects.--%>
      </a>
    </LEGEND>
    <br/>

	<p style="text-align: left;">
	    <fmt:message key="query.subclassConstraint"/><%--Constraint to be subtype:--%>
	    <html:select property="subclassValue" styleId="subclass1" disabled="true">
	        <c:forEach items="${SUBCLASSES[dec.path.type]}" var="subclass">
	            <html:option value="${subclass}">
	                <c:out value="${subclass}"/>
	            </html:option>
	        </c:forEach>
	    </html:select>
	    <html:submit property="subclass" styleId="subclassSubmit" disabled="true">
	        <fmt:message key="query.submitConstraint"/><%--Add to query--%>
	    </html:submit>
	</p>
	<br/>
	</FIELDSET>
</c:if>

<!-- 
   LOOP QUERY CONSTRAINT
-->
<c:if test="${!empty dec.candidateLoops && !empty dec.loopQueryOps}">
 <FIELDSET>
    <LEGEND>
    <a href="javascript:swapInputs('loopQuery');"> <fmt:message key="query.filterLoopQuery" /><%--Filter query results on the query loop.--%>
      </a>
    </LEGEND>
    <br/>
	<p style="text-align: left;">
    <fmt:message key="query.loopQueryConstraint"/><%--Constraint to another field:--%>
    <html:select property="loopQueryOp" styleId="loopQuery1" disabled="true">
        <c:forEach items="${dec.loopQueryOps}" var="loopOp">
            <html:option value="${loopOp.property}">
                <c:out value="${loopOp.label}"/>
            </html:option>
        </c:forEach>
    </html:select>
    <html:select property="loopQueryValue" styleId="loopQuery2" disabled="true">
        <c:forEach items="${dec.candidateLoops}" var="loopPath">
            <html:option value="${loopPath}">
                <c:out value="${fn:replace(loopPath, '.', ' > ')}"/>
            </html:option>
        </c:forEach>
    </html:select>

	<html:submit property="loop" styleId="loopQuerySubmit" disabled="true">
	    <fmt:message key="query.submitConstraint"/><%--Add to query--%>
	</html:submit>
	</p>
	<br/>
	</FIELDSET>
</c:if>

<!-- 
   NULL OR NOT NULL CONSTRAINT  
-->
  <c:if test="${dec.path.attribute && !dec.path.primitive}"> <!-- only if it is an attribute and primitive type -->
  <br/>
  <FIELDSET>
    <LEGEND>
    <a href="javascript:swapInputs('empty');">
            <fmt:message key="query.filterEmpty"/><%--Filter query results on this field having any value or not.--%>
        </a>
    </LEGEND>
    <br/>
    <p style="text-align: left;">
    	<c:set var="selectedValue" value="" />
        <c:if test="${dec.nullSelected}">
          <c:set var="selectedValue" value="${dec.selectedValue}" />
        </c:if>
        <input type="radio" name="nullConstraint" id="empty1" value="NULL" disabled <c:if test="${selectedValue == 'IS NULL'}">checked</c:if>/><fmt:message key="query.constraint.null"/>
        <input type="radio" name="nullConstraint" id="empty2" value="NotNULL" disabled <c:if test="${selectedValue == 'IS NOT NULL'}">checked</c:if>/><fmt:message key="query.constraint.notnull"/>
        &nbsp;
        <html:submit property="nullnotnull" styleId="emptySubmit" disabled="true">
        <fmt:message key="query.submitConstraint"/><%--Add to query--%>
        </html:submit>
    </p>
    <br/>
    </FIELDSET>
	</c:if>

</c:when>

  <c:otherwise><%-- joinStyleOnly is not empty--%>
      <h3><fmt:message key="query.joinHeading" /></h3> <%--2. Join type--%>
      <ol style="list-style:none">
        <li>
          <input type="radio" name="joinType" value="inner" id="inner" <c:if test="${joinType == 'inner'}">checked</c:if>/>
          <label for="inner">&nbsp;
            <fmt:message key="query.innerJoin">
              <fmt:param value="${dec.path.path.secondLastClassDescriptor.unqualifiedName}"/>
              <fmt:param value="${dec.path.path.lastClassDescriptor.unqualifiedName}"/>
            </fmt:message>
            <img border="0" src="images/join_inner.png" width="13" height="13" title="Inner join"/>
          </label>
        </li>
        <li>
          <input type="radio"  name="joinType" value="outer" id="outer" <c:if test="${joinType == 'outer'}">checked</c:if>/>
          <label for="outer">&nbsp;
            <fmt:message key="query.outerJoin">
              <fmt:param value="${dec.path.path.secondLastClassDescriptor.unqualifiedName}"/>
              <fmt:param value="${dec.path.path.lastClassDescriptor.unqualifiedName}"/>
            </fmt:message>
            <img border="0" src="images/join_outer.png" width="13" height="13" title="Outer join"/>
          </label>
        </li>
      </ol>
      <html:hidden property="useJoin" value="true"/>
      <c:if test="${! empty joinStyleOnly}">
        <html:submit property="joinStyle" styleId="joinStyleSubmit" >
          <fmt:message key="query.submitConstraint"/><%-- Submit button --%>
        </html:submit>
      </c:if>
    </c:otherwise>
  </c:choose>
  </div>
  </c:if>
	<html:hidden property="selectedConstraint" styleId="selectedConstraint" value="${dec.selectedConstraint}"/>
	
<script type="text/javascript">
  initConstraint(document.getElementById("selectedConstraint"));
</script>
</html:form>
<form>
</form>
</div>

<!-- /queryBuilderConstraint.jsp -->
