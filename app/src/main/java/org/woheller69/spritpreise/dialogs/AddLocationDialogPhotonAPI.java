package org.woheller69.spritpreise.dialogs;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.os.ConfigurationCompat;
import androidx.fragment.app.DialogFragment;

import com.android.volley.Response;
import com.android.volley.VolleyError;

import org.json.JSONArray;
import org.json.JSONObject;
import org.woheller69.spritpreise.BuildConfig;
import org.woheller69.spritpreise.R;
import org.woheller69.spritpreise.activities.ManageLocationsActivity;
import org.woheller69.spritpreise.database.City;
import org.woheller69.spritpreise.database.SQLiteHelper;
import org.woheller69.spritpreise.ui.util.photonApiCall;
import org.woheller69.spritpreise.ui.util.AutoSuggestAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class AddLocationDialogPhotonAPI extends DialogFragment {

    Activity activity;
    View rootView;
    SQLiteHelper database;

    private AutoCompleteTextView autoCompleteTextView;
    City selectedCity;

    private static final int TRIGGER_AUTO_COMPLETE = 100;
    private static final long AUTO_COMPLETE_DELAY = 300;
    private Handler handler;
    private AutoSuggestAdapter autoSuggestAdapter;
    String url="https://photon.komoot.io/api/?q=";
    String lang="default";

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
       if (context instanceof Activity){
            this.activity=(Activity) context;
        }
    }


    @NonNull
    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        Locale locale = ConfigurationCompat.getLocales(Resources.getSystem().getConfiguration()).get(0);
        //supported languages by photon.komoot.io API: default, en, de, fr, it
        if ((locale.getLanguage().equals("de"))||(locale.getLanguage().equals("en"))||(locale.getLanguage().equals("fr"))||(locale.getLanguage().equals("it")))                 {
            lang=locale.getLanguage();
        } else {
            lang="default";
        }


        LayoutInflater inflater = getActivity().getLayoutInflater();
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        View view = inflater.inflate(R.layout.dialog_add_location, null);

        rootView = view;

        builder.setView(view);
        builder.setTitle(getActivity().getString(R.string.dialog_add_label));

        this.database = SQLiteHelper.getInstance(getActivity());


        final WebView webview= rootView.findViewById(R.id.webViewAddLocation);
        webview.getSettings().setJavaScriptEnabled(true);
        webview.getSettings().setUserAgentString(BuildConfig.APPLICATION_ID+"/"+BuildConfig.VERSION_NAME);
        webview.setBackgroundColor(0x00000000);
        webview.setBackgroundResource(R.drawable.photon);

        autoCompleteTextView = (AutoCompleteTextView) rootView.findViewById(R.id.autoCompleteTvAddDialog);

        //Setting up the adapter for AutoSuggest
        autoSuggestAdapter = new AutoSuggestAdapter(requireContext(),
                R.layout.list_item_autocomplete);
        autoCompleteTextView.setThreshold(2);
        autoCompleteTextView.setAdapter(autoSuggestAdapter);

        autoCompleteTextView.setOnItemClickListener(
                new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view,
                                            int position, long id) {
                        selectedCity=autoSuggestAdapter.getObject(position);
                        //Hide keyboard to have more space
                        final InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(rootView.getWindowToken(), 0);
                        //Show city on map
                        webview.loadUrl("file:///android_asset/map.html?lat=" + selectedCity.getLatitude() + "&lon=" + selectedCity.getLongitude());
                    }
                });

        autoCompleteTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int
                    count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before,
                                      int count) {
                handler.removeMessages(TRIGGER_AUTO_COMPLETE);
                handler.sendEmptyMessageDelayed(TRIGGER_AUTO_COMPLETE,
                        AUTO_COMPLETE_DELAY);
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        handler = new Handler(Looper.getMainLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                if (msg.what == TRIGGER_AUTO_COMPLETE) {
                    if (!TextUtils.isEmpty(autoCompleteTextView.getText())) {
                        makeApiCall(autoCompleteTextView.getText().toString());
                    }
                }
                return false;
            }
        });

        builder.setPositiveButton(getActivity().getString(R.string.dialog_add_add_button), new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                performDone();
            }
        });

        builder.setNegativeButton(getActivity().getString(R.string.dialog_add_close_button), null);

        return builder.create();

    }
    private void makeApiCall(String text) {
        photonApiCall.make(getContext(), text, url,lang, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                //parsing logic, please change it as per your requirement
                List<String> stringList = new ArrayList<>();
                List<City> cityList = new ArrayList<>();
                try {
                    JSONObject responseObject = new JSONObject(response);
                    JSONArray array = responseObject.getJSONArray("features");
                    for (int i = 0; i < array.length(); i++) {
                        City city =new City();
                        String citystring="";
                        JSONObject jsonFeatures = array.getJSONObject(i);
                        JSONObject jsonProperties = jsonFeatures.getJSONObject("properties");
                        JSONObject jsonGeometry=jsonFeatures.getJSONObject("geometry");
                        JSONArray jsonCoordinates=jsonGeometry.getJSONArray("coordinates");
                        String name="";
                        if (jsonProperties.has("name")) {
                            name=jsonProperties.getString("name");
                            citystring=citystring+name+", ";
                        }
                        String postcode="";
                        if (jsonProperties.has("postcode")) {
                            postcode=jsonProperties.getString("postcode");
                            citystring=citystring+postcode+", ";
                        }
                        String cityname=name;
                        if (jsonProperties.has("city")) {
                            cityname=jsonProperties.getString("city");
                            citystring=citystring+cityname+", ";
                        }
                        String state="";
                        if (jsonProperties.has("state")) {
                            state=jsonProperties.getString("state");
                            citystring=citystring+state+", ";
                        }
                        String countrycode="";
                        if (jsonProperties.has("countrycode")) {
                            countrycode=jsonProperties.getString("countrycode");
                            citystring=citystring+countrycode;
                        }

                        city.setCityName(cityname);
                        city.setCountryCode(countrycode);
                        city.setLatitude((float) jsonCoordinates.getDouble(1));
                        city.setLongitude((float) jsonCoordinates.getDouble(0));
                        if (countrycode.equals("DE")) {
                            cityList.add(city);
                            stringList.add(citystring);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                //IMPORTANT: set data here and notify
                autoSuggestAdapter.setData(stringList,cityList);
                autoSuggestAdapter.notifyDataSetChanged();
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Handler h = new Handler(activity.getMainLooper());
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(activity, error.toString(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }


    private void performDone() {
        if (selectedCity == null) {
            Toast.makeText(activity, R.string.dialog_add_no_city_found, Toast.LENGTH_SHORT).show();
        }else {
            ((ManageLocationsActivity) activity).addCityToList(selectedCity);
            dismiss();
        }
    }

}
