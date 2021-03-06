/*
 * Copyright (C) 2009 University of Washington
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.tasks;

import org.apache.http.client.HttpClient;
import org.apache.http.protocol.HttpContext;
import org.javarosa.xform.parse.XFormParser;
import org.kxml2.kdom.Element;
import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.listeners.FormListDownloaderListener;
import org.odk.collect.android.logic.FormDetails;
import org.odk.collect.android.preferences.PreferencesActivity;
import org.odk.collect.android.utilities.DocumentFetchResult;
import org.odk.collect.android.utilities.WebUtils;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.HashMap;

/**
 * Background task for downloading forms from urls or a formlist from a url. We overload this task a
 * bit so that we don't have to keep track of two separate downloading tasks and it simplifies
 * interfaces. If LIST_URL is passed to doInBackground(), we fetch a form list. If a hashmap
 * containing form/url pairs is passed, we download those forms.
 * 
 * @author carlhartung
 */
public class DownloadFormListTask extends AsyncTask<Void, String, HashMap<String, FormDetails>> {
    private static final String t = "DownloadFormsTask";

    // used to store error message if one occurs
    public static final String DL_ERROR_MSG = "dlerrormessage";
    public static final String DL_AUTH_REQUIRED = "dlauthrequired";

    private FormListDownloaderListener mStateListener;

    private static final String NAMESPACE_OPENROSA_ORG_XFORMS_XFORMS_LIST =
        "http://openrosa.org/xforms/xformsList";


    private boolean isXformsListNamespacedElement(Element e) {
        return e.getNamespace().equalsIgnoreCase(NAMESPACE_OPENROSA_ORG_XFORMS_XFORMS_LIST);
    }


    /*
     * (non-Javadoc)
     * @see android.os.AsyncTask#doInBackground(java.lang.Object[])
     */
    @Override
    protected HashMap<String, FormDetails> doInBackground(Void... values) {
        SharedPreferences settings =
            PreferenceManager.getDefaultSharedPreferences(Collect.getInstance().getBaseContext());
        String downloadListUrl =
            settings.getString(PreferencesActivity.KEY_SERVER_URL,
                Collect.getInstance().getString(R.string.default_server_url));
        String downloadPath = settings.getString(PreferencesActivity.KEY_FORMLIST_URL, "/formlist");
        downloadListUrl += downloadPath;
        String auth = settings.getString(PreferencesActivity.KEY_AUTH, "");

        // We populate this with available forms from the specified server.
        // <formname, details>
        HashMap<String, FormDetails> formList = new HashMap<String, FormDetails>();

        // get shared HttpContext so that authentication and cookies are retained.
        HttpContext localContext = Collect.getInstance().getHttpContext();
        HttpClient httpclient = WebUtils.createHttpClient(WebUtils.CONNECTION_TIMEOUT);

        DocumentFetchResult result =
            WebUtils.getXmlDocument(downloadListUrl, localContext, httpclient, auth);

        // If we can't get the document, return the error, cancel the task
        if (result.errorMessage != null) {
            if (result.responseCode == 401) {
                formList.put(DL_AUTH_REQUIRED, new FormDetails(result.errorMessage));
            } else {
                formList.put(DL_ERROR_MSG, new FormDetails(result.errorMessage));
            }
            return formList;
        }

        if (result.isOpenRosaResponse) {
            // Attempt OpenRosa 1.0 parsing
            Element xformsElement = result.doc.getRootElement();
            if (!xformsElement.getName().equals("xforms")) {
                String error = "root element is not <xforms> : " + xformsElement.getName();
                Log.e(t, "Parsing OpenRosa reply -- " + error);
                formList.put(
                    DL_ERROR_MSG,
                    new FormDetails(Collect.getInstance().getString(
                        R.string.parse_openrosa_formlist_failed, error)));
                return formList;
            }
            String namespace = xformsElement.getNamespace();
            if (!isXformsListNamespacedElement(xformsElement)) {
                String error = "root element namespace is incorrect:" + namespace;
                Log.e(t, "Parsing OpenRosa reply -- " + error);
                formList.put(
                    DL_ERROR_MSG,
                    new FormDetails(Collect.getInstance().getString(
                        R.string.parse_openrosa_formlist_failed, error)));
                return formList;
            }
            int nElements = xformsElement.getChildCount();
            for (int i = 0; i < nElements; ++i) {
                if (xformsElement.getType(i) != Element.ELEMENT) {
                    // e.g., whitespace (text)
                    continue;
                }
                Element xformElement = (Element) xformsElement.getElement(i);
                if (!isXformsListNamespacedElement(xformElement)) {
                    // someone else's extension?
                    continue;
                }
                String name = xformElement.getName();
                if (!name.equalsIgnoreCase("xform")) {
                    // someone else's extension?
                    continue;
                }

                // this is something we know how to interpret
                String formId = null;
                String formName = null;
                String majorMinorVersion = null;
                String description = null;
                String downloadUrl = null;
                String manifestUrl = null;
                // don't process descriptionUrl
                int fieldCount = xformElement.getChildCount();
                for (int j = 0; j < fieldCount; ++j) {
                    if (xformElement.getType(j) != Element.ELEMENT) {
                        // whitespace
                        continue;
                    }
                    Element child = xformElement.getElement(j);
                    if (!isXformsListNamespacedElement(child)) {
                        // someone else's extension?
                        continue;
                    }
                    String tag = child.getName();
                    if (tag.equals("formID")) {
                        formId = XFormParser.getXMLText(child, true);
                        if (formId != null && formId.length() == 0) {
                            formId = null;
                        }
                    } else if (tag.equals("name")) {
                        formName = XFormParser.getXMLText(child, true);
                        if (formName != null && formName.length() == 0) {
                            formName = null;
                        }
                    } else if (tag.equals("majorMinorVersion")) {
                        majorMinorVersion = XFormParser.getXMLText(child, true);
                        if (majorMinorVersion != null && majorMinorVersion.length() == 0) {
                            majorMinorVersion = null;
                        }
                    } else if (tag.equals("descriptionText")) {
                        description = XFormParser.getXMLText(child, true);
                        if (description != null && description.length() == 0) {
                            description = null;
                        }
                    } else if (tag.equals("downloadUrl")) {
                        downloadUrl = XFormParser.getXMLText(child, true);
                        if (downloadUrl != null && downloadUrl.length() == 0) {
                            downloadUrl = null;
                        }
                    } else if (tag.equals("manifestUrl")) {
                        manifestUrl = XFormParser.getXMLText(child, true);
                        if (manifestUrl != null && manifestUrl.length() == 0) {
                            manifestUrl = null;
                        }
                    }
                }
                if (formId == null || downloadUrl == null || formName == null) {
                    String error =
                        "Forms list entry " + Integer.toString(i)
                                + " is missing one or more tags: formId, name, or downloadUrl";
                    Log.e(t, "Parsing OpenRosa reply -- " + error);
                    formList.clear();
                    formList.put(
                        DL_ERROR_MSG,
                        new FormDetails(Collect.getInstance().getString(
                            R.string.parse_openrosa_formlist_failed, error)));
                    return formList;
                }
                /*
                 * TODO: We currently don't care about major/minor version. maybe someday we will.
                 */
                // Integer modelVersion = null;
                // Integer uiVersion = null;
                // try {
                // if (majorMinorVersion == null || majorMinorVersion.length() == 0) {
                // modelVersion = null;
                // uiVersion = null;
                // } else {
                // int idx = majorMinorVersion.indexOf(".");
                // if (idx == -1) {
                // modelVersion = Integer.parseInt(majorMinorVersion);
                // uiVersion = null;
                // } else {
                // modelVersion = Integer.parseInt(majorMinorVersion.substring(0, idx));
                // uiVersion =
                // (idx == majorMinorVersion.length() - 1) ? null : Integer
                // .parseInt(majorMinorVersion.substring(idx + 1));
                // }
                // }
                // } catch (Exception e) {
                // e.printStackTrace();
                // String error = "Forms list entry " + Integer.toString(i) +
                // " has an invalid majorMinorVersion: " + majorMinorVersion;
                // Log.e(t, "Parsing OpenRosa reply -- " + error);
                // formList.clear();
                // formList.put(DL_ERROR_MSG, new FormDetails(
                // Collect.getInstance().getString(R.string.parse_openrosa_formlist_failed,
                // error)));
                // return formList;
                // }
                formList.put(formId, new FormDetails(formName, downloadUrl, manifestUrl, formId));
            }
        } else {
            // Aggregate 0.9.x mode...
            // populate HashMap with form names and urls
            Element formsElement = result.doc.getRootElement();
            int formsCount = formsElement.getChildCount();
            for (int i = 0; i < formsCount; ++i) {
                if (formsElement.getType(i) != Element.ELEMENT) {
                    // whitespace
                    continue;
                }
                Element child = formsElement.getElement(i);
                String tag = child.getName();
                String formId = null;
                if (tag.equals("formID")) {
                    formId = XFormParser.getXMLText(child, true);
                    if (formId != null && formId.length() == 0) {
                        formId = null;
                    }
                }
                if (tag.equalsIgnoreCase("form")) {
                    String formName = XFormParser.getXMLText(child, true);
                    if (formName != null && formName.length() == 0) {
                        formName = null;
                    }
                    String downloadUrl = child.getAttributeValue(null, "url");
                    downloadUrl = downloadUrl.trim();
                    if (downloadUrl != null && downloadUrl.length() == 0) {
                        downloadUrl = null;
                    }
                    if (downloadUrl == null || formName == null) {
                        String error =
                            "Forms list entry " + Integer.toString(i)
                                    + " is missing form name or url attribute";
                        Log.e(t, "Parsing OpenRosa reply -- " + error);
                        formList.clear();
                        formList.put(
                            DL_ERROR_MSG,
                            new FormDetails(Collect.getInstance().getString(
                                R.string.parse_legacy_formlist_failed, error)));
                        return formList;
                    }
                    formList.put(formName, new FormDetails(formName, downloadUrl, null, formName));
                }
            }
        }
        return formList;
    }


    /*
     * (non-Javadoc)
     * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
     */
    @Override
    protected void onPostExecute(HashMap<String, FormDetails> value) {
        synchronized (this) {
            if (mStateListener != null) {
                mStateListener.formListDownloadingComplete(value);
            }
        }
    }


    public void setDownloaderListener(FormListDownloaderListener sl) {
        synchronized (this) {
            mStateListener = sl;
        }
    }

}
