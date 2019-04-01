package obd.fz.com.myapplication;


import android.media.MediaPlayer;
import android.support.annotation.RawRes;

/**
 * 预警播报管理器
 */
public class AlarmManager {

    private MediaPlayer player;

    /**
     * 禁止构造
     */
    private AlarmManager() {
    }

    /**
     * 获得单例
     *
     * @return
     */
    public static AlarmManager getInstance() {
        return InstanceHolder.INSTANCE;
    }

    public void play(@RawRes int resId) {
        if (null != player && player.isPlaying()) {
            player.reset();
        }
        player = MediaPlayer.create(GlobalUtil.getContext(), resId);
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                if (player != null) {
                    player.reset();
                    player.release();
                    player = null;
                }
            }
        });
        player.start();
    }

    /**
     * 单例持有器
     */
    private static final class InstanceHolder {
        private static final AlarmManager INSTANCE = new AlarmManager();
    }
}
