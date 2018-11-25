package com.qmaker.survey.core.engines;

import com.qmaker.core.engines.QRunner;
import com.qmaker.core.entities.CopySheet;
import com.qmaker.core.entities.Exercise;
import com.qmaker.core.entities.Test;
import com.qmaker.core.interfaces.RunnableDispatcher;
import com.qmaker.core.io.QPackage;
import com.qmaker.survey.core.entities.PushOrder;
import com.qmaker.survey.core.entities.Survey;
import com.qmaker.survey.core.interfaces.PersistenceUnit;
import com.qmaker.survey.core.interfaces.PushProcess;
import com.qmaker.survey.core.interfaces.Pusher;
import com.qmaker.survey.core.utils.MemoryPersistenceUnit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import istat.android.base.tools.TextUtils;

public class QSurvey implements QRunner.StateListener, PushExecutor.ExecutionStateChangeListener {
    static QSurvey instance;
    final List<SurveyStateListener> listeners = new ArrayList<>();
    PersistenceUnit persistenceUnit = new MemoryPersistenceUnit();
    final PushExecutor pushExecutor = new PushExecutor();

    private QSurvey() {
        pushExecutor.registerExecutionStateChangeListener(this);
        pushExecutor.start();
        resetDefaultPusher();
    }

//    public QSurvey appendPusher(Pusher pusher) {
//        String supported = pusher.getSupportedGrandType();
////        pusherMap.put(supported, pusher);
//        return this;
//    }


    public PushExecutor getPushExecutor() {
        return pushExecutor;
    }

    public static QSurvey getInstance() {
        if (instance == null) {
            instance = new QSurvey();
            instance.init();
        }
        return instance;
    }


    private void init() {
        QRunner.getInstance().registerStateListener(0, this);
    }

    public QSurvey usePersistanceUnit(PersistenceUnit pUnit) {
        this.persistenceUnit = pUnit;
        return this;
    }

    @Override
    public boolean onRunnerPrepare(String uri) {
        return false;
    }

    @Override
    public boolean onRunnerPrepared(QRunner.PrepareResult result) {
        return false;
    }

    @Override
    public boolean onStartRunning(QPackage qPackage, Test test) {
        return false;
    }

    @Override
    public boolean onRunnerTimeTick(QPackage qPackage, Test test) {
        return false;
    }

    @Override
    public boolean onRunningExerciseChanged(QPackage qPackage, Test test, Exercise exercise, int exerciseIndex) {
        return false;
    }

    @Override
    public boolean onRunningTimeOut(QPackage qPackage, Test test) {
        return false;
    }

    @Override
    public boolean onRunCanceled(QPackage qPackage, Test test) {
        return false;
    }

    @Override
    public boolean onFinishRunning(QPackage qPackage, Test test) {
        try {
            Survey survey = Survey.from(qPackage);
            if (survey == null) {
                return false;
            }
            //TODO detecter si il sagit d'un Survey Aynchrone ou synchrone et empêcher l'exercise de prendre drat de la fin du Test.
            //getPusher(survey).push(test.getCopySheet(), createPushCallback(survey, test.getCopySheet()));
            dispatchSurveyCompleted(survey, test);
        } catch (Survey.InvalidSurveyException e) {
            //Nothing to do, qpackake is not a survey.
        }
        return true;
    }

    private void dispatchSurveyCompleted(Survey survey, Test test) {
        synchronized (listeners) {
            for (SurveyStateListener listener : listeners) {
                listener.onSurveyCompleted(survey, test.getCopySheet());
            }
        }
    }

//    //TODO implementer le push callback.
//    private Pusher.Callback createPushCallback(Survey survey, CopySheet copySheet) {
//        return null;
//    }

//    private Pusher getPusher(Survey survey) {
//        return pusherMap.get(survey.auth.getGrandType());
//    }

    @Override
    public boolean onResetRunner(QPackage qPackage, Test test) {
        return false;
    }

    public boolean registerSurveyStateListener(SurveyStateListener listener) {
        return registerSurveyStateListener(-1, listener);
    }

    public boolean registerSurveyStateListener(int priority, SurveyStateListener listener) {
        synchronized (listeners) {
            if (listeners.contains(listener)) {
                return false;
            }
            if (priority >= 0 && priority < listeners.size() - 1) {
                listeners.add(priority, listener);
            } else {
                listeners.add(listener);
            }
        }
        return true;
    }

    public boolean unregisterRunStateListener(SurveyStateListener listener) {
        synchronized (listeners) {
            if (listeners.contains(listener)) {
                return false;
            }
            listeners.add(listener);
        }
        return false;
    }

    public final static RunnableDispatcher DEFAULT_RUNNABLE_DISPATCHER = new RunnableDispatcher() {
        @Override
        public void dispatch(Runnable runnable, int delay) {
            if (runnable != null) {
                if (delay > 0) {
                    try {
                        Thread.sleep(delay);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                runnable.run();
            }
        }

        @Override
        public void cancel(Runnable runnable) {

        }

        @Override
        public void release() {

        }
    };
    static RunnableDispatcher defaultRunnableDispatcher = DEFAULT_RUNNABLE_DISPATCHER;

    public static void setDefaultRunnableDispatcher(RunnableDispatcher defaultRunnableDispatcher) {
        QSurvey.defaultRunnableDispatcher = defaultRunnableDispatcher != null ? defaultRunnableDispatcher : DEFAULT_RUNNABLE_DISPATCHER;
    }

    public static RunnableDispatcher getDefaultRunnableDispatcher() {
        return defaultRunnableDispatcher;
    }

    @Override
    public void onTaskStateChanged(PushExecutor.Task task) {
        if (persistenceUnit != null) {
            if (task.getState() == PushProcess.STATE_SUCCESS) {
                persistenceUnit.delete(task.getOrder());
            } else {
                persistenceUnit.persist(task.getOrder());
            }
        }
    }

    public interface SurveyStateListener {
        void onSurveyCompleted(Survey survey, CopySheet copySheet);
    }

    final static HashMap<String, Pusher> pusherMap = new HashMap();

    public static Pusher appendPusher(Pusher typeHandler) {
        if (typeHandler == null || TextUtils.isEmpty(typeHandler.getSupportedGrandType())) {
            return null;
        }
        return pusherMap.put(typeHandler.getSupportedGrandType(), typeHandler);
    }

    public static Pusher removePusher(String typeName) {
        return pusherMap.remove(typeName);
    }

    static Pusher getPusher(String grandType) {
        Pusher pusher = pusherMap.get(grandType);
        return pusher;
    }


    public static Pusher getPusher(PushOrder order) {
        if (order == null || order.getRepository() == null) {
            return null;
        }
        return getPusher(order.getRepository().getGrandType());
    }

    //TODO ajouter des pusher par défaut a partir d'ici.
    public static void resetDefaultPusher() {
//        appendPusher(Pusher.DEFAULT);
//        appendPusher(Pusher.PUT_IN_ORDER);
//        appendPusher(Pusher.TYPE_MATCH_COLUMN);
//        appendPusher(Pusher.FILL_IN_THE_BLANK);
    }

}
