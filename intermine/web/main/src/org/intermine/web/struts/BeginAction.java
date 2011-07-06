package org.intermine.web.struts;

/*
 * Copyright (C) 2002-2011 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang.StringUtils;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.intermine.api.InterMineAPI;
import org.intermine.api.bag.BagQueryConfig;
import org.intermine.api.mines.FriendlyMineManager;
import org.intermine.api.mines.Mine;
import org.intermine.api.profile.Profile;
import org.intermine.api.profile.TagManager;
import org.intermine.api.tag.TagNames;
import org.intermine.api.template.TemplateManager;
import org.intermine.api.template.TemplateQuery;
import org.intermine.model.userprofile.Tag;
import org.intermine.util.PropertiesUtil;
import org.intermine.util.TypeUtil;
import org.intermine.web.logic.session.SessionMethods;

/**
 * Prepare templates and news to be rendered on home page
 *
 * @author Tom Riley
 */
public class BeginAction extends InterMineAction
{
    private static final Integer MAX_TEMPLATES = new Integer(8);

    /**
     * @LinkedHashMap stores tabs of popular templates on the homepage
     */
    private static LinkedHashMap<String, HashMap<String, Object>> bagOfTabs;

     /**
     * Either display the query builder or redirect to project.sitePrefix.
     *
     * @param mapping The ActionMapping used to select this instance
     * @param form The optional ActionForm bean for this request (if any)
     * @param request The HTTP request we are processing
     * @param response The HTTP response we are creating
     * @return an ActionForward object defining where control goes next
     * @exception Exception if the application business logic throws an exception
     */
    public ActionForward execute(ActionMapping mapping,
            ActionForm form,
            HttpServletRequest request,
            HttpServletResponse response)
        throws Exception {

        HttpSession session = request.getSession();
        ServletContext servletContext = session.getServletContext();
        Set<String> errorKeys = SessionMethods.getErrorOnInitialiser(servletContext);
        if (errorKeys != null && !errorKeys.isEmpty()) {
            for (String errorKey : errorKeys) {
                recordError(new ActionMessage(errorKey), request);
            }
            return mapping.findForward("blockingError");
        }

        final InterMineAPI im = SessionMethods.getInterMineAPI(session);
        Properties properties = SessionMethods.getWebProperties(servletContext);

        // If GALAXY_URL is sent from a Galaxy server, then save it in the session; if not, read
        // the default value from web.properties and save it in the session
        if (request.getParameter("GALAXY_URL") != null) {
            request.getSession().setAttribute("GALAXY_URL",
                    request.getParameter("GALAXY_URL"));
            String msg = properties.getProperty("galaxy.welcomeMessage");
            SessionMethods.recordMessage(msg, session);
        } else {
            request.getSession().setAttribute(
                    "GALAXY_URL",
                    properties.getProperty("galaxy.baseurl.default")
                            + properties.getProperty("galaxy.url.value"));
        }

        List<TemplateQuery> templates = null;

        // do we have popular templates cached?
        if (bagOfTabs == null) {
            // ...get begin/homepage popular templates in tabs
            Properties props = PropertiesUtil.getPropertiesStartingWith("begin.tabs", properties);
            if (props.size() != 0) {
                props = PropertiesUtil.stripStart("begin.tabs", props);
                int i = 1;
                // init
                bagOfTabs = new LinkedHashMap<String, HashMap<String, Object>>();
                while (true) {
                    if (props.containsKey(i + ".id")) {
                        LinkedHashMap<String, Object> tab = new LinkedHashMap<String, Object>();
                        String identifier;

                        // identifier, has to be present
                        identifier = (String) props.get(i + ".id");
                        tab.put("identifier", identifier);
                        // (optional) description
                        tab.put("description", props.containsKey(i + ".description")
                                ? (String) props.get(i + ".description") : "");
                        tab.put("description", props.containsKey(i + ".description")
                                ? (String) props.get(i + ".description") : "");
                        // (optional) custom name, otherwise use identifier

                        tab.put("name", props.containsKey(i + ".name")
                                ? (String) props.get(i + ".name") : identifier);
                        // fetch the actual template queries
                        TemplateManager tm = im.getTemplateManager();
                        Profile profile = SessionMethods.getProfile(session);
                        if (profile.isLoggedIn()) {
                            templates = tm.getPopularTemplatesByAspect(
                                    TagNames.IM_ASPECT_PREFIX + identifier,
                                    MAX_TEMPLATES, profile.getUsername(),
                                    session.getId());
                        } else {
                            templates = tm.getPopularTemplatesByAspect(
                                    TagNames.IM_ASPECT_PREFIX + identifier,
                                    MAX_TEMPLATES);
                        }

                        if (templates.size() > MAX_TEMPLATES) {
                            templates = templates.subList(0, MAX_TEMPLATES);
                        }

                        tab.put("templates", templates);

                        bagOfTabs.put(Integer.toString(i), tab);
                        i++;
                    } else {
                        break;
                    }
                }
            }
        }
        request.setAttribute("tabs", bagOfTabs);

        // preferred bags (Gucci)
        List<String> preferredBags = new LinkedList<String>();
        TagManager tagManager = im.getTagManager();
        List<Tag> preferredBagTypeTags = tagManager.getTags(
                "im:preferredBagType", null, "class", im.getProfileManager().getSuperuser());
        for (Tag tag : preferredBagTypeTags) {
            preferredBags.add(TypeUtil.unqualifiedName(tag.getObjectIdentifier()));
        }
        Collections.sort(preferredBags);
        request.setAttribute("preferredBags", preferredBags);

        // organism dropdown on list upload
        // only implemented in metabolicMine right now
        BagQueryConfig bagQueryConfig = im.getBagQueryConfig();
        String extraClassName = bagQueryConfig.getExtraConstraintClassName();
        if (extraClassName != null) {
            final String extraClassDefaultValue = getDefaultValue(request, im);
            if (!StringUtils.isEmpty(extraClassDefaultValue)) {
                request.setAttribute("extraClassDefaultValue", extraClassDefaultValue);
            }

            request.setAttribute("extraBagQueryClass", TypeUtil.unqualifiedName(extraClassName));

            List extraClassFieldValues = BagBuildController.getFieldValues(im.getObjectStore(),
                    im.getObjectStoreSummary(), extraClassName, bagQueryConfig.getConstrainField());
            request.setAttribute("extraClassFieldValues", extraClassFieldValues);
        }

        // cookie business
        if (!hasUserVisited(request)) {
            // set cookie
            setUserVisitedCookie(response);
            // first visit
            request.setAttribute("isNewUser", Boolean.TRUE);
        } else {
            request.setAttribute("isNewUser", Boolean.FALSE);
        }

        return mapping.findForward("begin");
    }

    /**
     * Determine if this page has been visited before using a cookie
     * @param request HTTP Servlet Request
     * @return true if page has been visited
     */
    private boolean hasUserVisited(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (int i = 0; i < cookies.length; i++) {
            Cookie cookie = cookies[i];
            if ("visited".equals(cookie.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Set a cookie by visiting this page
     * @param response HTTP Servlet Response
     * @return response HTTP Servlet Response
     */
    private HttpServletResponse setUserVisitedCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("visited", "at some point...");
        // see you in a year
        cookie.setMaxAge(365 * 24 * 60 * 60);
        response.addCookie(cookie);
        return response;
    }

    private String getDefaultValue(HttpServletRequest request, InterMineAPI im) {
        Properties webProperties = SessionMethods.getWebProperties(request.getSession()
                .getServletContext());
        FriendlyMineManager linkManager = FriendlyMineManager.getInstance(im, webProperties);
        Mine mine = linkManager.getLocalMine();
        if (mine != null) {
            return mine.getDefaultValue();
        }
        return null;
    }
}
