package br.edu.puc_campinas.projetointegrado2018.portaazul;


import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Fragment;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.StringTokenizer;

public class FragmentLogin extends Fragment {
    public static final String PREFS_NAME = "PortaAzulPrefs";

    Button mLoginButton;
    EditText mUserText;
    EditText mPasswordText;
    TextView mTokenInfoText;

    public String token;

    public FragmentLogin() {

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_login, container, false);

        mLoginButton = (Button)view.findViewById(R.id.button_login);
        mPasswordText = (EditText)view.findViewById(R.id.textbox_password);
        mUserText = (EditText)view.findViewById(R.id.textbox_user);
        mTokenInfoText = (TextView)view.findViewById(R.id.textview_token_info);

        mLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Snackbar.make(view, "Muito bom", Snackbar.LENGTH_LONG)
                //        .setAction("Action", null).show();
                attemptLogin();
            }
        });

        mUserText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == EditorInfo.IME_ACTION_DONE || id == EditorInfo.IME_NULL) {
                    mPasswordText.requestFocus();
                    return true;
                }
                return false;
            }
        });

        mPasswordText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == EditorInfo.IME_ACTION_DONE || id == EditorInfo.IME_NULL) {
                    attemptLogin();
                    mPasswordText.setFocusableInTouchMode(false);
                    mPasswordText.setFocusable(false);
                    mPasswordText.setFocusableInTouchMode(true);
                    mPasswordText.setFocusable(true);
                    InputMethodManager imm = (InputMethodManager)getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(mPasswordText.getWindowToken(), 0);
                    return true;
                }
                return false;
            }
        });

        // recupera informacoes salvas
        SharedPreferences settings = getActivity().getSharedPreferences(PREFS_NAME, 0);
        String user = settings.getString("user", "");
        String token = settings.getString("token", "");
        String expira = settings.getString("expira", "");

        mTokenInfoText.setText("" + token + "\nExpira em " + expira);
        mUserText.setText(user);

        setRetainInstance(true);

        return view;
    }

    private void attemptLogin()
    {
        String user = mUserText.getText().toString();
        String password = mPasswordText.getText().toString();
        String door = "ESP32-PORTA1";
        String api = "http://192.168.43.159/pib/api/autoriza.php?";
        String param = "usuario&name="+user + "&password=" + password + "&porta=" + door;

        new GetUrlContentTask().execute(api + param);

        mTokenInfoText.setText("Aguarde...");

        SharedPreferences settings = getActivity().getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("user", user);
        editor.commit();
    }

    private class GetUrlContentTask extends AsyncTask<String, Integer, String> {
        protected String doInBackground(String... urls) {
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setDoOutput(true);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.connect();
                BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String content = "", line;
                while ((line = rd.readLine()) != null) {
                    content += line + "\n";
                }
                return content;
            } catch(MalformedURLException e) {

            } catch(ProtocolException e) {

            } catch(IOException e) {

            }
            return "";
        }

        protected void onProgressUpdate(Integer... progress) {
        }

        protected void onPostExecute(String result) {
            // this is executed on the main thread after the process is over
            // update your UI here

            Log.i("onPostExecute", result);
            if (result.contains("ERRO_INTERNO")) {
                mTokenInfoText.setText("Erro interno do servidor");
            }
            else if (result.contains("ACESSO_NEGADO")) {
                mTokenInfoText.setText("Credenciais invalidas");
            }
            else if (result.contains("OK")) {
                StringTokenizer st = new StringTokenizer(result, "|");
                st.nextToken();
                String token = st.nextToken();
                String expira =st.nextToken();
                mTokenInfoText.setText("" + token + "\nExpira em " + expira);

                // salva as informacoes
                SharedPreferences settings = getActivity().getSharedPreferences(PREFS_NAME, 0);
                SharedPreferences.Editor editor = settings.edit();
                editor.putString("token", token);
                editor.putString("expira", expira);
                editor.commit();

                Log.i("onPostExecute", "" + token + "\nExpira em " + expira);
            }
            else {
                mTokenInfoText.setText("Erro fatal");
                Log.e("onPostExecute", result);
            }
        }
    }
}
