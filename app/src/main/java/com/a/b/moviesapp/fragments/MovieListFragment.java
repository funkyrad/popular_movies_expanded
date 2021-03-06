package com.a.b.moviesapp.fragments;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.a.b.moviesapp.other.Constants;
import com.a.b.moviesapp.RecyclerViewAdapter;
import com.a.b.moviesapp.other.MainInterface;
//import com.a.b.moviesapp.other.RefreshGridView;
import com.a.b.moviesapp.pojo.Movie;
import com.a.b.moviesapp.R;
import com.a.b.moviesapp.RecyclerClickListener;
import com.a.b.moviesapp.pojo.ReviewResult;
import com.a.b.moviesapp.pojo.Youtube;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import de.greenrobot.event.EventBus;

/**
 * Main view for the list of clickable movie posters. Uses a RecyclerView to display all movie posters.
 *
 * Created by Andrew on 1/1/2016.
 */

public class MovieListFragment extends Fragment implements RecyclerClickListener {

    MainInterface.MovieInterface mListener;
    RecyclerView mRecyclerView;
    private RecyclerViewAdapter mGridViewAdapter;
    private GridLayoutManager mLayout;
    private ArrayList<Movie> mMovieArray;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.grid_fragment, container, false);

        /* Detects whether the device is a tablet or phone, tablet has 3 columns, phone has 2*/
        if(getResources().getBoolean(R.bool.isTablet)) {
            mLayout = new GridLayoutManager(getActivity(), 3);
        }else{
            mLayout = new GridLayoutManager(getActivity(), 2);
        }

        mRecyclerView = (RecyclerView) view.findViewById(R.id.recycler_view);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLayoutManager(mLayout);
        mGridViewAdapter = new RecyclerViewAdapter(getActivity(), this);
        mRecyclerView.setAdapter(mGridViewAdapter);
        mGridViewAdapter.setList(mMovieArray);

        return view;
    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        if (savedInstanceState == null) {
            getMovies(Constants.MOST_POPULAR);
        } else {
            mMovieArray = savedInstanceState.getParcelableArrayList("movies");
        }
    }
    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_main, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_item_popular_sort) {
            getMovies(Constants.MOST_POPULAR);
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.action_popular_sort);

        } else if (id==R.id.menu_item_highest_rated) {
            getMovies(Constants.HIGHEST_RATED);
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.action_highest_rated);

        } else if (id==R.id.menu_item_favorited) {
            getFavorites();
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.action_favorited);
        }
        mListener.holdOldTitle();
        return super.onOptionsItemSelected(item);
    }

    /**
     * Pulls the stored movies (favorites) from the ContentProvider, which pulls data from the SQLite database.
     * Each cursor row is parsed and placed into a Movie object. Movie trailers and reviews are stored in the
     * database as a JSON array. Here it retrieves the JSON array and parses the data into an arrayList of YouTube
     * and ReviewResult objects (there may be more than one trailer and/or review) and places this arrayList
     * into the Movies object.
     */
    public void getFavorites() {
        String[] selection=new String[]{Constants.TITLE};
        Cursor cursor = getContext().getContentResolver().query(Uri.parse(Constants.CONTENT_AUTHORITY + getString(R.string.get_stored_movies)), null, null, null, null);
        if (cursor != null && cursor.getCount() > 0) {
            cursor.moveToFirst();
            ArrayList<Movie> movies = new ArrayList<>();
            do {
                Movie m = new Movie();

                m.setId(cursor.getInt(cursor.getColumnIndex(Constants.MOVIE_ID)));
                m.setMovieTitle(cursor.getString(cursor.getColumnIndex(Constants.TITLE)));
                m.setPosterUrl(cursor.getString(cursor.getColumnIndex(Constants.POSTER_PATH)));
                m.setBackDropUrl(cursor.getString(cursor.getColumnIndex(Constants.BACKDROP_PATH)));
                m.setDate(cursor.getString(cursor.getColumnIndex(Constants.DATE)));
                m.setRating(cursor.getDouble(cursor.getColumnIndex(Constants.RATING)));
                m.setSummary(cursor.getString(cursor.getColumnIndex(Constants.OVERVIEW)));
                m.setFavorite(cursor.getInt(cursor.getColumnIndex(Constants.FAVORITED)) == 1 ? Boolean.TRUE : Boolean.FALSE);

                String trailers = cursor.getString(cursor.getColumnIndex(Constants.TRAILERS));
                String reviews = cursor.getString(cursor.getColumnIndex(Constants.REVIEWS));

                List<Youtube> movieTrailers = new ArrayList<>();

                try {
                    JSONArray jsonArray = new JSONArray(trailers);

                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject jsonObject = jsonArray.getJSONObject(i);
                        Youtube youTube = new Youtube();

                        youTube.name = jsonObject.has("name") ? jsonObject.getString("name") : "";
                        youTube.setSource(jsonObject.has("source") ? jsonObject.getString("source") : "");
                        youTube.size = jsonObject.has("size") ? jsonObject.getString("size") : "";
                        youTube.type = jsonObject.has("type") ? jsonObject.getString("type") : "";

                        movieTrailers.add(youTube);
                    }
                    m.setTrailer(movieTrailers);

                } catch (JSONException e) {
                    e.printStackTrace();
                }

                List<ReviewResult> reviewResults = new ArrayList<>();

                try {
                    JSONObject jsonObj = new JSONObject(reviews);
                    JSONArray jsonArray = jsonObj.has("results") ? jsonObj.getJSONArray("results") : null;
                    if (jsonArray != null) {
                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject jsonObject = jsonArray.getJSONObject(i);
                            ReviewResult review = new ReviewResult();

                            review.id = jsonObject.has("id") ? jsonObject.getString("id") : "";
                            review.author = (jsonObject.has("author") ? jsonObject.getString("author") : "");
                            review.content = jsonObject.has("content") ? jsonObject.getString("content") : "";
                            review.url = jsonObject.has("url") ? jsonObject.getString("url") : "";

                            reviewResults.add(review);
                        }
                        m.setReviews(reviewResults);

                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                movies.add(m);

            } while (cursor.moveToNext());
            cursor.close();
            mMovieArray = movies;

            mGridViewAdapter.setList(movies);
            mRecyclerView.scrollToPosition(0);

            if(getResources().getBoolean(R.bool.isTablet)) {
                mListener.openDetailFragment(mMovieArray.get(0));
            }

        } else {
            mMovieArray = null;
            mGridViewAdapter.setList(null);
            Toast.makeText(getActivity(), R.string.no_movies_favorited, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Bundle bundle = new Bundle();
        bundle.putParcelableArrayList("movies", mMovieArray);
        onSaveInstanceState(bundle);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putParcelableArrayList("movies", mMovieArray);
        super.onSaveInstanceState(outState);
    }

    public void getMovies(String order) {
        FetchMoviesTask fetchMoviesTask = new FetchMoviesTask();
        fetchMoviesTask.execute(order);
    }

    @Override
    public void recyclerClicked(View v, int position) {
        mListener.openDetailFragment(mMovieArray.get(position));
    }

    /**
     * This AsyncTask does the heavy lifting of pulling basic movie details from themoviedb api. The api is
     * set up to pull all data seen in the details page except the reviews and trailers. Those are handled with
     * Retrofit in another part of this app under a separate api call.
     */
    public class FetchMoviesTask extends AsyncTask<String, Void, ArrayList<Movie>> {
        private final String LOG_TAG = MovieListFragment.class.getSimpleName();

        @Override
        protected ArrayList<Movie> doInBackground(String... params) {
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;
            String jsonString = null;

            try {
                Uri builtUri = Uri.parse(Constants.BASE_URL).buildUpon()
                    .appendPath(params[0])
                    .appendQueryParameter(Constants.API_KEY, Constants.API_KEY_STRING)
                    .build();

                URL url = new URL(builtUri.toString());

                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {
                    return null;
                }
                jsonString = buffer.toString();

            } catch (IOException e) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getActivity(), R.string.dead_network, Toast.LENGTH_LONG).show();
                    }
                });
                return null;

            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                    }
                }
            }
            try {
                return parseJson(jsonString);
            } catch (JSONException e) {
                Log.e(LOG_TAG, e.getMessage(), e);
                e.printStackTrace();
            }
            return null;
        }

        /**
         * Function created to provide complete logging. The JSON responses tend to overfill logcat
         * @param TAG tag for logging
         * @param message message for specific logging
         */
        public void logMore(String TAG, String message) {
            int maxLogSize = 2000;
            for(int i = 0; i <= message.length() / maxLogSize; i++) {
                int start = i * maxLogSize;
                int end = (i+1) * maxLogSize;
                end = end > message.length() ? message.length() : end;
                android.util.Log.d(TAG, message.substring(start, end));
            }
        }

        private ArrayList<Movie> parseJson(String JsonStr) throws JSONException {
            mMovieArray = new ArrayList<Movie>();
            JSONObject MoviesJson = new JSONObject(JsonStr);
            JSONArray moviesArray = MoviesJson.has("results") ? MoviesJson.getJSONArray("results") : null;
            if (moviesArray != null) {
                for (int i = 0; i < moviesArray.length(); i++) {

                    JSONObject movieObj = moviesArray.getJSONObject(i);
                    Movie movie = new Movie();

                    movie.mTitle = movieObj.has(Constants.TITLE) ? movieObj.getString(Constants.TITLE) : "";
                    movie.mOverview = movieObj.has(Constants.OVERVIEW) ? movieObj.getString(Constants.OVERVIEW) : "";
                    movie.mPosterUrl = movieObj.has(Constants.POSTER_PATH) ? movieObj.getString(Constants.POSTER_PATH) : "";
                    movie.mBackDropUrl = movieObj.has(Constants.BACKDROP_PATH) ? movieObj.getString(Constants.BACKDROP_PATH) : "";
                    movie.mDate = movieObj.has(Constants.DATE) ? movieObj.getString(Constants.DATE) : "";
                    movie.mVoteAverage = movieObj.has(Constants.RATING) ? movieObj.getDouble(Constants.RATING) : null;
                    movie.mId = movieObj.has(Constants.ID) ? movieObj.getInt(Constants.ID):null;

                    mMovieArray.add(movie);
                }
            }
            return mMovieArray;
        }

        @Override
        protected void onPostExecute(final ArrayList<Movie> result) {
            if (mMovieArray != null) {
                mGridViewAdapter.setList(result);
                mRecyclerView.scrollToPosition(0);

                if(getResources().getBoolean(R.bool.isTablet)) {
                    mListener.openDetailFragment(mMovieArray.get(0));
                }
            }
        }
    }

    /**
     * For attaching to the activity so this class can use the implemented MovieInterface on the MainActivity
     * @param context provided by Android
     */

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof MainInterface.MovieInterface) {
            mListener = (MainInterface.MovieInterface) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement MainInterface.MovieInterface");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }
}