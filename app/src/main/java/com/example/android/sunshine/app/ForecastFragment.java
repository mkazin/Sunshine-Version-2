package com.example.android.sunshine.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

import static com.example.android.sunshine.app.BuildConfig.OpenWeatherMapApiKey;

/**
 * A placeholder fragment containing a simple view.
 */
public class ForecastFragment extends Fragment {

    public ArrayAdapter<String> forecastAdapter;

    public ForecastFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //        TODO: need this??? registerForContextMenu(rootView);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        forecastAdapter = new ArrayAdapter<>(getActivity(), R.layout.list_item_forecast, R.id.list_item_forecast_textview, new ArrayList());

        ListView forecast_list = (ListView) rootView.findViewById(R.id.listview_forecast);
        forecast_list.setAdapter(forecastAdapter);

        forecast_list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long row) {

                String forecast = forecastAdapter.getItem(position);

                Intent detailIntent = new Intent(getActivity(), DetailActivity.class);
                detailIntent.putExtra(DetailActivity.EXTRA_FORECAST_NAME, forecast);
                getActivity().startActivity(detailIntent);
            }
        });
        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        updateWeather();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.forecastfragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_refresh) {
            updateWeather();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateWeather() {
        FetchWeatherTask fetchTask = new FetchWeatherTask();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String location = preferences.getString(
                getString(R.string.pref_location_key),
                getString(R.string.pref_location_default));
        String[] fetchParams = new String[]{location};
        fetchTask.execute(location);
    }

    public enum WeatherTaskProgress {
        WTP_INITIALIZED,
        WTP_CONNECTING,
        WTP_RECEIVING_DATA,
        WTP_COMPILING_DATA
    }
    public class FetchWeatherTask extends AsyncTask<String, WeatherTaskProgress, String[]> {

        private String LOG_TAG = FetchWeatherTask.class.getSimpleName();

        // TODO: put this somewhere else?
        private int numDays = 7;

        @Override
        protected String[] doInBackground(String[] params) {

            publishProgress(WeatherTaskProgress.WTP_INITIALIZED);

            // These two need to be declared outside the try/catch
            // so that they can be closed in the finally block.
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            // Will contain the raw JSON response as a string.
            String forecastJsonStr = null;

            try {
                // Construct the URL for the OpenWeatherMap query
                // Possible parameters are avaiable at OWM's forecast API page, at
                // http://openweathermap.org/API#forecast

                // NOTE: Somerville, MA is ID 4951257
                // For a lookup table, see here: http://bulk.openweathermap.org/sample/
                Uri.Builder builder = Uri.parse("http://api.openweathermap.org/data/2.5/forecast/daily").buildUpon();
                builder.appendQueryParameter("q", params[0]);
                builder.appendQueryParameter("mode", "json");
                builder.appendQueryParameter("units", "metric");
                builder.appendQueryParameter("cnt", String.valueOf(numDays));
                builder.appendQueryParameter("APPID", OpenWeatherMapApiKey);
                Uri uri = builder.build();

                URL url = new URL(uri.toString());
                // Create the request to OpenWeatherMap, and open the connection
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                publishProgress(WeatherTaskProgress.WTP_CONNECTING);
                urlConnection.connect();

                // Read the input stream into a String
                InputStream inputStream = urlConnection.getInputStream();
                publishProgress(WeatherTaskProgress.WTP_RECEIVING_DATA);
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    // Nothing to do.
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                    // But it does make debugging a *lot* easier if you print out the completed
                    // buffer for debugging.
                    buffer.append(line).append("\n");
                }

                if (buffer.length() == 0) {
                    // Stream was empty.  No point in parsing.
                    return null;
                }
                publishProgress(WeatherTaskProgress.WTP_COMPILING_DATA);
                forecastJsonStr = buffer.toString();
            } catch (IOException e) {
                Log.e(LOG_TAG, "Error ", e);
                // If the code didn't successfully get the weather data, there's no point in attemping
                // to parse it.
                return null;
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e(LOG_TAG, "Error closing stream", e);
                    }
                }
            }

            try {
                return getWeatherDataFromJson(forecastJsonStr, numDays);
            } catch (JSONException e) {
                Log.e(LOG_TAG, "Failed to parse weather data (set to Verbose to see returned data)", e);
                Log.v(LOG_TAG, forecastJsonStr);
                return null;
            }
        }


        @Override
        protected void onProgressUpdate(WeatherTaskProgress[] values) {
            super.onProgressUpdate(values);
        }

        @Override
        protected void onPostExecute(String[] strings) {
            super.onPostExecute(strings);

            // Do nothing on failure
            if (strings != null) {
                forecastAdapter.clear();
                for (String curr : strings) {
                    forecastAdapter.add(curr);
                }
            }
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
        }


        /* The date/time conversion code is going to be moved outside the asynctask later,
         * so for convenience we're breaking it out into its own method now.
         */
        private String getReadableDateString(long time) {
            // Because the API returns a unix timestamp (measured in seconds),
            // it must be converted to milliseconds in order to be converted to valid date.
            SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MMM dd");
            return shortenedDateFormat.format(time);
        }

        /**
         * Prepare the weather high/lows for presentation.
         */
        private String formatHighLows(double high, double low) {
            // For presentation, assume the user doesn't care about tenths of a degree.
            long roundedHigh = Math.round(high);
            long roundedLow = Math.round(low);

            String highLowStr = roundedHigh + "/" + roundedLow;
            return highLowStr;
        }

        /**
         * Take the String representing the complete forecast in JSON Format and
         * pull out the data we need to construct the Strings needed for the wireframes.
         * <p/>
         * Fortunately parsing is easy:  constructor takes the JSON string and converts it
         * into an Object hierarchy for us.
         */
        private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays)
                throws JSONException {

            // These are the names of the JSON objects that need to be extracted.
            final String OWM_LIST = "list";
            final String OWM_WEATHER = "weather";
            final String OWM_TEMPERATURE = "temp";
            final String OWM_MAX = "max";
            final String OWM_MIN = "min";
            final String OWM_DESCRIPTION = "main";

            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

            // OWM returns daily forecasts based upon the local time of the city that is being
            // asked for, which means that we need to know the GMT offset to translate this data
            // properly.

            // Since this data is also sent in-order and the first day is always the
            // current day, we're going to take advantage of that to get a nice
            // normalized UTC date for all of our weather.

            Time dayTime = new Time();
            dayTime.setToNow();

            // we start at the day returned by local time. Otherwise this is a mess.
            int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);

            // now we work exclusively in UTC
            dayTime = new Time();

            String[] resultStrs = new String[numDays];
            for (int i = 0; i < weatherArray.length(); i++) {
                // For now, using the format "Day, description, hi/low"
                String day;
                String description;
                String highAndLow;

                // Get the JSON object representing the day
                JSONObject dayForecast = weatherArray.getJSONObject(i);

                // The date/time is returned as a long.  We need to convert that
                // into something human-readable, since most people won't read "1400356800" as
                // "this saturday".
                long dateTime;
                // Cheating to convert this to UTC time, which is what we want anyhow
                dateTime = dayTime.setJulianDay(julianStartDay + i);
                day = getReadableDateString(dateTime);

                // description is in a child array called "weather", which is 1 element long.
                JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                description = weatherObject.getString(OWM_DESCRIPTION);

                // Temperatures are in a child object called "temp".  Try not to name variables
                // "temp" when working with temperature.  It confuses everybody.
                JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);

                double high = temperatureObject.getDouble(OWM_MAX);
                double low = temperatureObject.getDouble(OWM_MIN);

                // Convert to Imperial if preferences so
                String unit_key = PreferenceManager.getDefaultSharedPreferences(getActivity())
                        .getString(
                                getActivity().getString(R.string.pref_units_key),
                                getActivity().getString(R.string.pref_units_metric));
                if (unit_key != null && unit_key.contentEquals(getActivity().getString(R.string.pref_units_imperial_key))) {
                    high = celsiusToFahrenheit(high);
                    low = celsiusToFahrenheit(low);
                }

                highAndLow = formatHighLows(high, low);
                resultStrs[i] = day + " - " + description + " - " + highAndLow;
            }

            return resultStrs;
        }
    }
    private double celsiusToFahrenheit(final double src) {
        return (src * 1.8) + 32.0;
    }

}
