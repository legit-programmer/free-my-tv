package io.freemytv;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Remote-friendly controls; opening this screen never changes packages. */
public class TuningActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Map<String, CheckBox> choices = new LinkedHashMap<>();
    private LinearLayout page, apps;
    private TextView status;
    private Button scan, apply, restore;
    private boolean closed, pending;
    private long previousFinish, pendingSince;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.init(this);
        ScrollView scroll = new ScrollView(this);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (32 * getResources().getDisplayMetrics().density);
        page.setPadding(pad, pad, pad, pad);
        scroll.addView(page);
        setContentView(scroll);
        text(Ui.tr("Desempenho e apps", "Performance & Apps"), 28);
        text(Ui.tr("As opções visuais são aplicadas ao voltar ao Início.",
                "Visual changes apply when you return Home."), 18);
        toggle("lightweight", Ui.tr("Modo leve: fundo estático, imagens menores, descanso só com relógio",
                "Lightweight mode: static background, smaller images, clock-only screensaver"), Performance.lightweight);
        toggle("recommendations", Ui.tr("Recomendações TMDB (precisa de chave)", "TMDB recommendations (requires your key)"), Performance.recommendations);
        toggle("weather", Ui.tr("Clima e horários do sol", "Weather and sunrise/sunset"), Performance.weather);
        toggle("prices", Ui.tr("Cotações de criptomoedas", "Crypto prices"), Performance.prices);
        text(Recs.hasKey(this) ? Ui.tr("Chave TMDB configurada.", "TMDB key configured.")
                : Ui.tr("Para recomendações, configure tmdb_key e recompile.", "For recommendations, configure tmdb_key and rebuild."), 18);
        text("This product uses the TMDB API but is not endorsed or certified by TMDB.", 16);
        text(Ui.tr("Desativar apps opcionais", "Disable optional apps"), 24);
        text(Ui.tr("Nada é selecionado automaticamente. Apps já desativados não são alterados. O assistente controla o botão de voz. Espelhamento e serviços TCL podem parar se selecionados. Pacotes Xiaomi ainda precisam de inventário e revisão.",
                "Nothing is selected automatically. Previously disabled apps are untouched. Assistant packages control voice search. Selecting mirroring or TCL services disables those features. Xiaomi packages still need inventory and review."), 18);
        status = text(Ui.tr("Ative a depuração e aceite o aviso ADB ao procurar apps.",
                "Enable debugging and accept the ADB prompt when scanning."), 18);
        scan = button(Ui.tr("Procurar apps instalados", "Scan installed apps"), this::scan);
        apps = new LinearLayout(this);
        apps.setOrientation(LinearLayout.VERTICAL);
        page.addView(apps);
        apply = button(Ui.tr("Aplicar seleção", "Apply selection"), () -> confirm(false));
        restore = button(Ui.tr("Restaurar apps opcionais", "Restore optional apps"), () -> confirm(true));
        apply.setEnabled(false);
        restore.setEnabled(false);
        button(Ui.tr("Voltar", "Back"), this::finish);
    }

    private TextView text(String value, int size) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setPadding(0, 10, 0, 10);
        page.addView(t); return t;
    }
    private Button button(String label, Runnable action) {
        Button b = new Button(this); b.setText(label);
        b.setOnClickListener(v -> action.run()); page.addView(b); return b;
    }
    private void toggle(String key, String label, boolean value) {
        CheckBox b = new CheckBox(this); b.setText(label); b.setTextSize(20); b.setChecked(value);
        b.setOnCheckedChangeListener((v, checked) -> Performance.set(this, key, checked));
        page.addView(b);
    }
    private void enableActions(boolean value) {
        scan.setEnabled(value); apply.setEnabled(value && !choices.isEmpty()); restore.setEnabled(value);
        for (CheckBox b : choices.values()) b.setEnabled(value && !Boolean.TRUE.equals(b.getTag()));
    }
    private void scan() {
        if (pending) return;
        enableActions(false);
        status.setText(Ui.tr("Procurando… aceite a depuração na TV.", "Scanning… accept debugging on the TV."));
        worker.execute(() -> {
            try {
                JSONObject inventory = HomeMode.debloatInventory(getApplicationContext());
                main.post(() -> {
                    if (closed) return;
                    apps.removeAllViews(); choices.clear();
                    JSONArray rows = inventory.optJSONArray("rows");
                    for (int i = 0; rows != null && i < rows.length(); i++) {
                        JSONObject row = rows.optJSONObject(i);
                        if (row == null) continue;
                        String pkg = row.optString("package");
                        boolean external = row.optBoolean("external");
                        CheckBox b = new CheckBox(this);
                        String label = pkg;
                        try { label = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString() + "\n" + pkg; }
                        catch (android.content.pm.PackageManager.NameNotFoundException ignored) {}
                        b.setText(label + (external ? Ui.tr(" — já desativado", " — already disabled") : ""));
                        b.setTextSize(18); b.setChecked(row.optBoolean("selected")); b.setTag(external);
                        apps.addView(b); choices.put(pkg, b);
                    }
                    status.setText(choices.isEmpty() ? Ui.tr("Nenhum candidato conhecido instalado. Nenhum app foi alterado.",
                            "No known candidates installed. No apps were changed.") : Ui.tr("Marque apenas os apps que deseja desativar.",
                            "Check only the apps you want to disable."));
                    enableActions(true);
                });
            } catch (Exception e) {
                main.post(() -> { if (!closed) { status.setText(Ui.tr("Falha na leitura: ", "Scan failed: ") + e.getMessage()); scan.setEnabled(true); } });
            }
        });
    }
    private void confirm(boolean reset) {
        JSONArray selected = new JSONArray();
        if (!reset) for (Map.Entry<String, CheckBox> e : choices.entrySet())
            if (e.getValue().isEnabled() && e.getValue().isChecked()) selected.put(e.getKey());
        String message = reset ? Ui.tr("Restaurar somente apps opcionais desativados por este app?", "Restore only optional apps disabled by this app?")
                : Ui.tr("Desativar os selecionados e restaurar os desmarcados que este app desativou?\n", "Disable selected apps and restore unchecked apps previously disabled here?\n") + selected;
        new AlertDialog.Builder(this).setTitle(Ui.tr("Confirmar alterações", "Confirm changes")).setMessage(message)
                .setNegativeButton(Ui.tr("Cancelar", "Cancel"), null)
                .setPositiveButton(Ui.tr("Aplicar", "Apply"), (d, w) -> {
                    previousFinish = HomeMode.state(this).optLong("debloatFinished");
                    pending = true; pendingSince = android.os.SystemClock.uptimeMillis(); enableActions(false);
                    status.setText(Ui.tr("Aplicando… aceite a depuração se solicitado.", "Applying… accept debugging if prompted."));
                    ModeJob.schedule(this, ModeJob.DEBLOAT, selected.toString(), 0);
                    main.post(poll);
                }).show();
    }
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (closed || !pending) return;
            JSONObject state = HomeMode.state(TuningActivity.this);
            if (state.optLong("debloatFinished") != previousFinish) {
                pending = false;
                String error = state.optString("error");
                status.setText(error.isEmpty() ? Ui.tr("Concluído. Procure novamente para conferir.", "Done. Scan again to verify.") : error);
                // Rescan before another apply so a failed selection cannot be mistaken for success.
                scan.setEnabled(true); restore.setEnabled(true); apply.setEnabled(false);
                return;
            }
            if (android.os.SystemClock.uptimeMillis() - pendingSince > 150_000)
                status.setText(Ui.tr("Ainda aguardando o serviço. Confira a depuração e os logs; nenhuma conclusão foi confirmada.",
                        "Still waiting for the service. Check debugging and logs; completion is not confirmed."));
            main.postDelayed(this, 1000);
        }
    };
    @Override protected void onDestroy() {
        closed = true; main.removeCallbacksAndMessages(null); worker.shutdownNow();
        super.onDestroy();
    }
}
