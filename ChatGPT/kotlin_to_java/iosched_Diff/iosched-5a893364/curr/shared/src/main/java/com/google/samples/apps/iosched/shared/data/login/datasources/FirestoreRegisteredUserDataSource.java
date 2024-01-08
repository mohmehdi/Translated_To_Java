package com.google.samples.apps.iosched.shared.data.login.datasources;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.samples.apps.iosched.shared.domain.internal.DefaultScheduler;
import com.google.samples.apps.iosched.shared.result.Result;
import timber.log.Timber;
import javax.inject.Inject;

public class FirestoreRegisteredUserDataSource implements RegisteredUserDataSource {

    private static final String USERS_COLLECTION = "users";
    private static final String REGISTERED_KEY = "registered";

    private ListenerRegistration registeredChangedListenerSubscription;
    private MutableLiveData<Result<Boolean>> result = new MutableLiveData<>();

    @Inject
    public FirestoreRegisteredUserDataSource(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public void listenToUserChanges(String userId) {
        Timber.d("Observing firestore for changes in registration for: " + userId);

        if (registeredChangedListenerSubscription != null) {
            registeredChangedListenerSubscription.remove();
        }

        registeredChangedListenerSubscription = firestore.collection(USERS_COLLECTION)
                .document(userId).addSnapshotListener((snapshot, exception) -> {
            DefaultScheduler.execute(() -> {
                Boolean isRegistered = (Boolean) snapshot.get(REGISTERED_KEY);
                Timber.d("Received registered flag: " + isRegistered);
                result.postValue(Result.Success(isRegistered));
            });
        });
    }

    @Override
    public LiveData<Result<Boolean>> observeResult() {
        return result;
    }

    @Override
    public void clearListener() {
        if (registeredChangedListenerSubscription != null) {
            registeredChangedListenerSubscription.remove();
        }
    }
}