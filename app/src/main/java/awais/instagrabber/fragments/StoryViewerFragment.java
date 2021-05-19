package awais.instagrabber.fragments;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.GestureDetectorCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.transition.TransitionManager;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.controller.BaseControllerListener;
import com.facebook.drawee.interfaces.DraweeController;
import com.facebook.imagepipeline.image.ImageInfo;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import awais.instagrabber.R;
import awais.instagrabber.activities.MainActivity;
import awais.instagrabber.adapters.StoriesAdapter;
import awais.instagrabber.asyncs.CreateThreadAction;
import awais.instagrabber.customviews.helpers.VerticalSpaceItemDecoration;
import awais.instagrabber.customviews.stickers.QuestionStickerView;
import awais.instagrabber.databinding.FragmentStoryViewerBinding;
import awais.instagrabber.interfaces.SwipeEvent;
import awais.instagrabber.models.StoryModel;
import awais.instagrabber.models.enums.MediaItemType;
import awais.instagrabber.models.stickers.PollModel;
import awais.instagrabber.models.stickers.QuestionModel;
import awais.instagrabber.models.stickers.QuizModel;
import awais.instagrabber.models.stickers.SliderModel;
import awais.instagrabber.repositories.requests.StoryViewerOptions;
import awais.instagrabber.repositories.requests.StoryViewerOptions.Type;
import awais.instagrabber.repositories.requests.directmessages.BroadcastOptions;
import awais.instagrabber.repositories.responses.User;
import awais.instagrabber.repositories.responses.VideoVersion;
import awais.instagrabber.repositories.responses.directmessages.DirectThreadBroadcastResponse;
import awais.instagrabber.repositories.responses.story.StoryMedia;
import awais.instagrabber.repositories.responses.story.StoryPoll;
import awais.instagrabber.repositories.responses.story.StoryQuestion;
import awais.instagrabber.repositories.responses.story.StorySlider;
import awais.instagrabber.repositories.responses.story.StorySticker;
import awais.instagrabber.utils.Constants;
import awais.instagrabber.utils.CookieUtils;
import awais.instagrabber.utils.DownloadUtils;
import awais.instagrabber.utils.ResponseBodyUtils;
import awais.instagrabber.utils.StickerFactory;
import awais.instagrabber.utils.TextUtils;
import awais.instagrabber.utils.Utils;
import awais.instagrabber.viewmodels.ArchivesViewModel;
import awais.instagrabber.viewmodels.FeedStoriesViewModel;
import awais.instagrabber.viewmodels.HighlightsViewModel;
import awais.instagrabber.viewmodels.StoriesViewModel;
import awais.instagrabber.viewmodels.StoryViewerFragmentViewModel;
import awais.instagrabber.webservices.DirectMessagesService;
import awais.instagrabber.webservices.MediaService;
import awais.instagrabber.webservices.StoriesService;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static awais.instagrabber.utils.Utils.settingsHelper;

public class StoryViewerFragment extends Fragment implements QuestionStickerView.OnQuestionStickerClickListener {
    private static final String TAG = "StoryViewerFragment";

    @Nullable
    private MainActivity fragmentActivity;
    private View root;
    private FragmentStoryViewerBinding binding;
    private String currentStoryUsername;
    private String highlightTitle;
    private StoriesAdapter storyItemsAdapter;
    private SwipeEvent swipeEvent;
    private GestureDetectorCompat gestureDetector;
    private StoriesService storiesService;
    private MediaService mediaService;
    private StoryModel currentStoryItem;
    private int slidePos;
    private int lastSlidePos;
    private PollModel poll;
    private QuestionModel question;
    private String[] mentions;
    private QuizModel quiz;
    private SliderModel slider;
    private MenuItem menuDownload;
    private MenuItem menuDm;
    private MenuItem menuProfile;
    private SimpleExoPlayer player;
    private String actionBarTitle;
    private String actionBarSubtitle;
    private boolean fetching = false;
    private boolean sticking = false;
    private boolean shouldRefresh = true;
    private boolean downloadVisible = false;
    private boolean dmVisible = false;
    private boolean profileVisible = true;
    private int currentFeedStoryIndex;
    private double sliderValue;
    private StoryViewerFragmentViewModel viewModel;
    private StoriesViewModel<?> storiesViewModel;
    private DirectMessagesService directMessagesService;
    private Drawable originalToolbarBg;
    private Drawable originalCollapsingToolbarBg;
    private Drawable originalAppbarBg;
    private ViewOutlineProvider originalAppbarOutlineProvider;

    private final String cookie = settingsHelper.getString(Constants.COOKIE);
    private StoryViewerOptions options;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String csrfToken = CookieUtils.getCsrfTokenFromCookie(cookie);
        if (csrfToken == null) return;
        final long userIdFromCookie = CookieUtils.getUserIdFromCookie(cookie);
        final String deviceId = settingsHelper.getString(Constants.DEVICE_UUID);
        viewModel = new ViewModelProvider(this).get(StoryViewerFragmentViewModel.class);
        fragmentActivity = (MainActivity) getActivity();
        if (fragmentActivity == null) return;
        storiesService = StoriesService.getInstance(csrfToken, userIdFromCookie, deviceId);
        mediaService = MediaService.getInstance(null, null, 0);
        directMessagesService = DirectMessagesService.getInstance(csrfToken, userIdFromCookie, deviceId);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable final Bundle savedInstanceState) {
        if (root != null) {
            shouldRefresh = false;
            return root;
        }
        binding = FragmentStoryViewerBinding.inflate(inflater, container, false);
        root = binding.getRoot();
        return root;
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable final Bundle savedInstanceState) {
        if (!shouldRefresh) return;
        init();
        shouldRefresh = false;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu, final MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.story_menu, menu);
        menuDownload = menu.findItem(R.id.action_download);
        menuDm = menu.findItem(R.id.action_dms);
        menuProfile = menu.findItem(R.id.action_profile);
        menuDownload.setVisible(downloadVisible);
        menuDm.setVisible(dmVisible);
        menuProfile.setVisible(profileVisible);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull final Menu menu) {
        // hide menu items from activity
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        final Context context = getContext();
        if (context == null) return false;
        int itemId = item.getItemId();
        if (itemId == R.id.action_download) {
            if (ContextCompat.checkSelfPermission(context, DownloadUtils.PERMS[0]) == PackageManager.PERMISSION_GRANTED) {
                // downloadStory();
            } else {
                ActivityCompat.requestPermissions(requireActivity(), DownloadUtils.PERMS, 8020);
            }
            return true;
        }
        if (itemId == R.id.action_dms) {
            final EditText input = new EditText(context);
            input.setHint(R.string.reply_hint);
            new AlertDialog.Builder(context)
                    .setTitle(R.string.reply_story)
                    .setView(input)
                    .setPositiveButton(R.string.confirm, (d, w) -> new CreateThreadAction(cookie, currentStoryItem.getUserId(), thread -> {
                        if (thread == null) {
                            Toast.makeText(context, R.string.downloader_unknown_error, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        try {
                            final Call<DirectThreadBroadcastResponse> request = directMessagesService
                                    .broadcastStoryReply(BroadcastOptions.ThreadIdOrUserIds.of(thread.getThreadId()),
                                                         input.getText().toString(),
                                                         currentStoryItem.getStoryMediaId(),
                                                         String.valueOf(currentStoryItem.getUserId()));
                            request.enqueue(new Callback<DirectThreadBroadcastResponse>() {
                                @Override
                                public void onResponse(@NonNull final Call<DirectThreadBroadcastResponse> call,
                                                       @NonNull final Response<DirectThreadBroadcastResponse> response) {
                                    if (!response.isSuccessful()) {
                                        Toast.makeText(context, R.string.downloader_unknown_error, Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    Toast.makeText(context, R.string.answered_story, Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onFailure(@NonNull final Call<DirectThreadBroadcastResponse> call, @NonNull final Throwable t) {
                                    try {
                                        Toast.makeText(context, R.string.downloader_unknown_error, Toast.LENGTH_SHORT).show();
                                        Log.e(TAG, "onFailure: ", t);
                                    } catch (Throwable ignored) {}
                                }
                            });
                        } catch (UnsupportedEncodingException e) {
                            Log.e(TAG, "Error", e);
                        }
                    }).execute())
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        }
        if (itemId == R.id.action_profile) {
            // openProfile("@" + currentStory.getUsername());
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 8020 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // downloadStory();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (player != null) {
            player.pause();
        }
        if (fragmentActivity != null) {
            fragmentActivity.getToolbar().setBackground(originalToolbarBg);
            fragmentActivity.getCollapsingToolbarView().setBackground(originalCollapsingToolbarBg);
            fragmentActivity.getAppbarLayout().setBackground(originalAppbarBg);
            fragmentActivity.getAppbarLayout().setOutlineProvider(originalAppbarOutlineProvider);
            fragmentActivity.resetNavHostScrollBehavior();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (fragmentActivity != null) {
            final ActionBar actionBar = fragmentActivity.getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(actionBarTitle);
                actionBar.setSubtitle(actionBarSubtitle);
            }
            binding.getRoot().postDelayed(() -> {
                originalToolbarBg = fragmentActivity.getToolbar().getBackground();
                originalCollapsingToolbarBg = fragmentActivity.getCollapsingToolbarView().getBackground();
                originalAppbarBg = fragmentActivity.getAppbarLayout().getBackground();
                originalAppbarOutlineProvider = fragmentActivity.getAppbarLayout().getOutlineProvider();
                fragmentActivity.getAppbarLayout().setBackground(new ColorDrawable(Color.TRANSPARENT));
                fragmentActivity.getAppbarLayout().setOutlineProvider(null);
                fragmentActivity.getCollapsingToolbarView().setBackground(new ColorDrawable(Color.TRANSPARENT));
                fragmentActivity.getToolbar().setBackground(new ColorDrawable(Color.TRANSPARENT));
                fragmentActivity.removeNavHostScrollBehavior();
            }, 500);
        }
        setHasOptionsMenu(true);
    }

    @Override
    public void onDestroy() {
        // releasePlayer();
        // reset subtitle
        // final ActionBar actionBar = fragmentActivity.getSupportActionBar();
        // if (actionBar != null) {
        //     actionBar.setSubtitle(null);
        // }
        super.onDestroy();
    }

    private void init() {
        if (fragmentActivity == null || getArguments() == null) return;
        final StoryViewerFragmentArgs fragmentArgs = StoryViewerFragmentArgs.fromBundle(getArguments());
        options = fragmentArgs.getOptions();
        viewModel.setOptions(options);
        // currentFeedStoryIndex = options.getStoryIndex();
        // if (currentFeedStoryIndex < 0) {
        //     currentFeedStoryIndex = 0;
        // }
        final Type type = options.getType();
        switch (type) {
            case HIGHLIGHT:
                storiesViewModel = new ViewModelProvider(fragmentActivity).get(HighlightsViewModel.class);
                break;
            case STORY_ARCHIVE:
                storiesViewModel = new ViewModelProvider(fragmentActivity).get(ArchivesViewModel.class);
                break;
            default:
            case FEED_STORY:
                storiesViewModel = new ViewModelProvider(fragmentActivity).get(FeedStoriesViewModel.class);
                break;
        }
        viewModel.setStories(storiesViewModel.getList().getValue());
        setupThumbnails();
        setupObservers();
        viewModel.init();
        // binding.getRoot().setOnStickerClickListener(this);
    }

    private void setupThumbnails() {
        // setupListeners();
        final Context context = getContext();
        if (context == null) return;
        final LinearLayoutManager layoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false);
        binding.currentStoryItems.setLayoutManager(layoutManager);
        final VerticalSpaceItemDecoration itemDecoration = new VerticalSpaceItemDecoration(Utils.convertDpToPx(4));
        binding.currentStoryItems.addItemDecoration(itemDecoration);
        storyItemsAdapter = new StoriesAdapter((model, position) -> {
            viewModel.setActiveStoryItemIndex(position);
        });
        binding.currentStoryItems.setAdapter(storyItemsAdapter);
        // viewModel.getList().observe(fragmentActivity, storiesAdapter::submitList);
        // resetView();
    }

    private void setupObservers() {
        viewModel.getCurrentReel().observe(getViewLifecycleOwner(), reel -> {
            if (storyItemsAdapter == null) return;
            storyItemsAdapter.submitList(reel != null ? reel.getItems() : null);
        });
        viewModel.getActiveStoryMedia().observe(getViewLifecycleOwner(), storyMedia -> {
            if (storyMedia == null) {
                hideAllItemViews();
                return;
            }
            final MediaItemType itemType = storyMedia.getMediaType();
            switch (itemType) {
                case MEDIA_TYPE_IMAGE:
                    setupImage(storyMedia);
                    break;
                case MEDIA_TYPE_VIDEO:
                    setupVideo(storyMedia);
                    break;
                case MEDIA_TYPE_LIVE:
                    break;
                case MEDIA_TYPE_SLIDER:
                case MEDIA_TYPE_VOICE:
                default:
                    return;
            }
            setupUserDetails(storyMedia);
            setupStickers(storyMedia);
        });
        viewModel.getActiveStoryItemIndex().observe(getViewLifecycleOwner(),
                                                    index -> storyItemsAdapter.setActiveIndex(index == null ? 0 : index));
    }

    private void setupUserDetails(@NonNull final StoryMedia storyMedia) {
        final User user = storyMedia.getUser();
        if (user == null) return;
        binding.profilePic.setImageURI(user.getProfilePicUrl());
        binding.username.setUsername(user.getUsername(), user.isVerified());
    }

    private void hideAllItemViews() {
        binding.profilePic.setImageURI((Uri) null);
        binding.username.setUsername("");
        binding.progressView.setVisibility(View.GONE);
        binding.playerView.setVisibility(View.GONE);
        binding.imageViewer.setVisibility(View.GONE);
    }

    // @SuppressLint("ClickableViewAccessibility")
    // private void setupListeners() {
    //     final boolean hasFeedStories;
    //     List<?> models = null;
    //     if (currentFeedStoryIndex >= 0) {
    //         final Type type = options.getType();
    //         switch (type) {
    //             case HIGHLIGHT:
    //                 final HighlightsViewModel highlightsViewModel = (HighlightsViewModel) storiesViewModel;
    //                 models = highlightsViewModel.getList().getValue();
    //                 break;
    //             case FEED_STORY_POSITION:
    //                 final FeedStoriesViewModel feedStoriesViewModel = (FeedStoriesViewModel) storiesViewModel;
    //                 models = feedStoriesViewModel.getList().getValue();
    //                 break;
    //             case STORY_ARCHIVE:
    //                 final ArchivesViewModel archivesViewModel = (ArchivesViewModel) storiesViewModel;
    //                 models = archivesViewModel.getList().getValue();
    //                 break;
    //         }
    //     }
    //     hasFeedStories = models != null && !models.isEmpty();
    //     final List<?> finalModels = models;
    //     final Context context = getContext();
    //     if (context == null) return;
    //     swipeEvent = isRightSwipe -> {
    //         final List<StoryModel> storyModels = viewModel.getList().getValue();
    //         final int storiesLen = storyModels == null ? 0 : storyModels.size();
    //         if (sticking) {
    //             Toast.makeText(context, R.string.follower_wait_to_load, Toast.LENGTH_SHORT).show();
    //             return;
    //         }
    //         if (storiesLen <= 0) return;
    //         final boolean isLeftSwipe = !isRightSwipe;
    //         final boolean endOfCurrentStories = slidePos + 1 >= storiesLen;
    //         final boolean swipingBeyondCurrentStories = (endOfCurrentStories && isLeftSwipe) || (slidePos == 0 && isRightSwipe);
    //         if (swipingBeyondCurrentStories && hasFeedStories) {
    //             final int index = currentFeedStoryIndex;
    //             if ((isRightSwipe && index == 0) || (isLeftSwipe && index == finalModels.size() - 1)) {
    //                 Toast.makeText(context, R.string.no_more_stories, Toast.LENGTH_SHORT).show();
    //                 return;
    //             }
    //             final Object feedStoryModel = isRightSwipe
    //                                           ? finalModels.get(index - 1)
    //                                           : finalModels.size() == index + 1 ? null : finalModels.get(index + 1);
    //             paginateStories(feedStoryModel, finalModels.get(index), context, isRightSwipe, currentFeedStoryIndex == finalModels.size() - 2);
    //             return;
    //         }
    //         if (isRightSwipe) {
    //             if (--slidePos <= 0) {
    //                 slidePos = 0;
    //             }
    //         } else if (++slidePos >= storiesLen) {
    //             slidePos = storiesLen - 1;
    //         }
    //         currentStory = storyModels.get(slidePos);
    //         refreshStory();
    //     };
    //     gestureDetector = new GestureDetectorCompat(context, new SwipeGestureListener(swipeEvent));
    //     binding.playerView.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
    //     final GestureDetector.SimpleOnGestureListener simpleOnGestureListener = new GestureDetector.SimpleOnGestureListener() {
    //         @Override
    //         public boolean onFling(final MotionEvent e1, final MotionEvent e2, final float velocityX, final float velocityY) {
    //             final float diffX = e2.getX() - e1.getX();
    //             try {
    //                 if (Math.abs(diffX) > Math.abs(e2.getY() - e1.getY()) && Math.abs(diffX) > SWIPE_THRESHOLD
    //                         && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
    //                     swipeEvent.onSwipe(diffX > 0);
    //                     return true;
    //                 }
    //             } catch (final Exception e) {
    //                 // if (logCollector != null)
    //                 //     logCollector.appendException(e, LogCollector.LogFile.ACTIVITY_STORY_VIEWER, "setupListeners",
    //                 //                                  new Pair<>("swipeEvent", swipeEvent),
    //                 //                                  new Pair<>("diffX", diffX));
    //                 if (BuildConfig.DEBUG) Log.e(TAG, "Error", e);
    //             }
    //             return false;
    //         }
    //     };
    //
    //     if (hasFeedStories) {
    //         binding.btnBackward.setVisibility(currentFeedStoryIndex == 0 ? View.INVISIBLE : View.VISIBLE);
    //         binding.btnForward.setVisibility(currentFeedStoryIndex == finalModels.size() - 1 ? View.INVISIBLE : View.VISIBLE);
    //         binding.btnBackward.setOnClickListener(v -> paginateStories(finalModels.get(currentFeedStoryIndex - 1),
    //                                                                     finalModels.get(currentFeedStoryIndex),
    //                                                                     context, true, false));
    //         binding.btnForward.setOnClickListener(v -> paginateStories(finalModels.get(currentFeedStoryIndex + 1),
    //                                                                    finalModels.get(currentFeedStoryIndex),
    //                                                                    context, false,
    //                                                                    currentFeedStoryIndex == finalModels.size() - 2));
    //     }
    //
    //     binding.imageViewer.setTapListener(simpleOnGestureListener);
    //     binding.spotify.setOnClickListener(v -> {
    //         final Object tag = v.getTag();
    //         if (tag instanceof CharSequence) {
    //             Utils.openURL(context, tag.toString());
    //         }
    //     });
    //     binding.swipeUp.setOnClickListener(v -> {
    //         final Object tag = v.getTag();
    //         if (tag instanceof CharSequence) {
    //             Utils.openURL(context, tag.toString());
    //         }
    //     });
    //     binding.viewStoryPost.setOnClickListener(v -> {
    //         final Object tag = v.getTag();
    //         if (!(tag instanceof CharSequence)) return;
    //         final String mediaId = tag.toString();
    //         final AlertDialog alertDialog = new AlertDialog.Builder(context)
    //                 .setCancelable(false)
    //                 .setView(R.layout.dialog_opening_post)
    //                 .create();
    //         alertDialog.show();
    //         mediaService.fetch(Long.parseLong(mediaId), new ServiceCallback<Media>() {
    //             @Override
    //             public void onSuccess(final Media feedModel) {
    //                 final NavController navController = NavHostFragment.findNavController(StoryViewerFragment.this);
    //                 final Bundle bundle = new Bundle();
    //                 bundle.putSerializable(PostViewV2Fragment.ARG_MEDIA, feedModel);
    //                 try {
    //                     navController.navigate(R.id.action_global_post_view, bundle);
    //                 } catch (Exception e) {
    //                     Log.e(TAG, "openPostDialog: ", e);
    //                 }
    //             }
    //
    //             @Override
    //             public void onFailure(final Throwable t) {
    //                 alertDialog.dismiss();
    //                 Toast.makeText(context, R.string.downloader_unknown_error, Toast.LENGTH_SHORT).show();
    //             }
    //         });
    //     });
    //     final View.OnClickListener storyActionListener = v -> {
    //         final Object tag = v.getTag();
    //         if (tag instanceof PollModel) {
    //             poll = (PollModel) tag;
    //             if (poll.getMyChoice() > -1) {
    //                 new AlertDialog.Builder(context)
    //                         .setTitle(R.string.voted_story_poll)
    //                         .setAdapter(new ArrayAdapter<>(
    //                                             context,
    //                                             android.R.layout.simple_list_item_1,
    //                                             new String[]{
    //                                                     (poll.getMyChoice() == 0 ? "√ " : "") + poll.getLeftChoice() + " (" + poll.getLeftCount() + ")",
    //                                                     (poll.getMyChoice() == 1 ? "√ " : "") + poll.getRightChoice() + " (" + poll.getRightCount() + ")"
    //                                             }),
    //                                     null)
    //                         .setPositiveButton(R.string.ok, null)
    //                         .show();
    //             } else {
    //                 new AlertDialog.Builder(context)
    //                         .setTitle(poll.getQuestion())
    //                         .setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, new String[]{
    //                                 poll.getLeftChoice() + " (" + poll.getLeftCount() + ")",
    //                                 poll.getRightChoice() + " (" + poll.getRightCount() + ")"
    //                         }), (d, w) -> {
    //                             sticking = true;
    //                             storiesService.respondToPoll(
    //                                     currentStory.getStoryMediaId().split("_")[0],
    //                                     poll.getId(),
    //                                     w,
    //                                     new ServiceCallback<StoryStickerResponse>() {
    //                                         @Override
    //                                         public void onSuccess(final StoryStickerResponse result) {
    //                                             sticking = false;
    //                                             try {
    //                                                 poll.setMyChoice(w);
    //                                                 Toast.makeText(context, R.string.votef_story_poll, Toast.LENGTH_SHORT).show();
    //                                             } catch (Exception ignored) {}
    //                                         }
    //
    //                                         @Override
    //                                         public void onFailure(final Throwable t) {
    //                                             sticking = false;
    //                                             Log.e(TAG, "Error responding", t);
    //                                             try {
    //                                                 Toast.makeText(context, R.string.downloader_unknown_error, Toast.LENGTH_SHORT).show();
    //                                             } catch (Exception ignored) {}
    //                                         }
    //                                     });
    //                         })
    //                         .setPositiveButton(R.string.cancel, null)
    //                         .show();
    //             }
    //         } else if (tag instanceof QuestionModel) {
    //             question = (QuestionModel) tag;
    //             final EditText input = new EditText(context);
    //             input.setHint(R.string.answer_hint);
    //             new AlertDialog.Builder(context)
    //                     .setTitle(question.getQuestion())
    //                     .setView(input)
    //                     .setPositiveButton(R.string.confirm, (d, w) -> {
    //                         sticking = true;
    //                         storiesService.respondToQuestion(
    //                                 currentStory.getStoryMediaId().split("_")[0],
    //                                 question.getId(),
    //                                 input.getText().toString(),
    //                                 new ServiceCallback<StoryStickerResponse>() {
    //                                     @Override
    //                                     public void onSuccess(final StoryStickerResponse result) {
    //                                         sticking = false;
    //                                         try {
    //                                             Toast.makeText(context, R.string.answered_story, Toast.LENGTH_SHORT).show();
    //                                         } catch (Exception ignored) {}
    //                                     }
    //
    //                                     @Override
    //                                     public void onFailure(final Throwable t) {
    //                                         sticking = false;
    //                                         Log.e(TAG, "Error responding", t);
    //                                         try {
    //                                             Toast.makeText(context, R.string.downloader_unknown_error, Toast.LENGTH_SHORT).show();
    //                                         } catch (Exception ignored) {}
    //                                     }
    //                                 });
    //                     })
    //                     .setNegativeButton(R.string.cancel, null)
    //                     .show();
    //         } else if (tag instanceof String[]) {
    //             mentions = (String[]) tag;
    //             new AlertDialog.Builder(context)
    //                     .setTitle(R.string.story_mentions)
    //                     .setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, mentions), (d, w) -> openProfile(mentions[w]))
    //                     .setPositiveButton(R.string.cancel, null)
    //                     .show();
    //         } else if (tag instanceof QuizModel) {
    //             String[] choices = new String[quiz.getChoices().length];
    //             for (int q = 0; q < choices.length; ++q) {
    //                 choices[q] = (quiz.getMyChoice() == q ? "√ " : "") + quiz.getChoices()[q] + " (" + quiz.getCounts()[q] + ")";
    //             }
    //             new AlertDialog.Builder(context)
    //                     .setTitle(quiz.getMyChoice() > -1 ? getString(R.string.story_quizzed) : quiz.getQuestion())
    //                     .setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, choices), (d, w) -> {
    //                         if (quiz.getMyChoice() == -1) {
    //                             sticking = true;
    //                             storiesService.respondToQuiz(
    //                                     currentStory.getStoryMediaId().split("_")[0],
    //                                     quiz.getId(),
    //                                     w,
    //                                     new ServiceCallback<StoryStickerResponse>() {
    //                                         @Override
    //                                         public void onSuccess(final StoryStickerResponse result) {
    //                                             sticking = false;
    //                                             try {
    //                                                 quiz.setMyChoice(w);
    //                                                 Toast.makeText(context, R.string.answered_story, Toast.LENGTH_SHORT).show();
    //                                             } catch (Exception ignored) {}
    //                                         }
    //
    //                                         @Override
    //                                         public void onFailure(final Throwable t) {
    //                                             sticking = false;
    //                                             Log.e(TAG, "Error responding", t);
    //                                             try {
    //                                                 Toast.makeText(context, R.string.downloader_unknown_error, Toast.LENGTH_SHORT).show();
    //                                             } catch (Exception ignored) {}
    //                                         }
    //                                     });
    //                         }
    //                     })
    //                     .setPositiveButton(R.string.cancel, null)
    //                     .show();
    //         } else if (tag instanceof SliderModel) {
    //             slider = (SliderModel) tag;
    //             NumberFormat percentage = NumberFormat.getPercentInstance();
    //             percentage.setMaximumFractionDigits(2);
    //             LinearLayout sliderView = new LinearLayout(context);
    //             sliderView.setLayoutParams(new LinearLayout.LayoutParams(
    //                     LinearLayout.LayoutParams.MATCH_PARENT,
    //                     LinearLayout.LayoutParams.WRAP_CONTENT));
    //             sliderView.setOrientation(LinearLayout.VERTICAL);
    //             TextView tv = new TextView(context);
    //             tv.setGravity(Gravity.CENTER_HORIZONTAL);
    //             final SeekBar input = new SeekBar(context);
    //             double avg = slider.getAverage() * 100;
    //             input.setProgress((int) avg);
    //             sliderView.addView(input);
    //             sliderView.addView(tv);
    //             if (slider.getMyChoice().isNaN() && slider.canVote()) {
    //                 input.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
    //                     @Override
    //                     public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
    //                         sliderValue = progress / 100.0;
    //                         tv.setText(percentage.format(sliderValue));
    //                     }
    //
    //                     @Override
    //                     public void onStartTrackingTouch(SeekBar seekBar) {
    //                     }
    //
    //                     @Override
    //                     public void onStopTrackingTouch(SeekBar seekBar) {
    //                     }
    //                 });
    //                 new AlertDialog.Builder(context)
    //                         .setTitle(TextUtils.isEmpty(slider.getQuestion()) ? slider.getEmoji() : slider.getQuestion())
    //                         .setMessage(getResources().getQuantityString(R.plurals.slider_info,
    //                                                                      slider.getVoteCount(),
    //                                                                      slider.getVoteCount(),
    //                                                                      percentage.format(slider.getAverage())))
    //                         .setView(sliderView)
    //                         .setPositiveButton(R.string.confirm, (d, w) -> {
    //                             sticking = true;
    //                             storiesService.respondToSlider(
    //                                     currentStory.getStoryMediaId().split("_")[0],
    //                                     slider.getId(),
    //                                     sliderValue,
    //                                     new ServiceCallback<StoryStickerResponse>() {
    //                                         @Override
    //                                         public void onSuccess(final StoryStickerResponse result) {
    //                                             sticking = false;
    //                                             try {
    //                                                 slider.setMyChoice(sliderValue);
    //                                                 Toast.makeText(context, R.string.answered_story, Toast.LENGTH_SHORT).show();
    //                                             } catch (Exception ignored) {}
    //                                         }
    //
    //                                         @Override
    //                                         public void onFailure(final Throwable t) {
    //                                             sticking = false;
    //                                             Log.e(TAG, "Error responding", t);
    //                                             try {
    //                                                 Toast.makeText(context, R.string.downloader_unknown_error, Toast.LENGTH_SHORT).show();
    //                                             } catch (Exception ignored) {}
    //                                         }
    //                                     });
    //                         })
    //                         .setNegativeButton(R.string.cancel, null)
    //                         .show();
    //             } else {
    //                 input.setEnabled(false);
    //                 tv.setText(getString(R.string.slider_answer, percentage.format(slider.getMyChoice())));
    //                 new AlertDialog.Builder(context)
    //                         .setTitle(TextUtils.isEmpty(slider.getQuestion()) ? slider.getEmoji() : slider.getQuestion())
    //                         .setMessage(getResources().getQuantityString(R.plurals.slider_info,
    //                                                                      slider.getVoteCount(),
    //                                                                      slider.getVoteCount(),
    //                                                                      percentage.format(slider.getAverage())))
    //                         .setView(sliderView)
    //                         .setPositiveButton(R.string.ok, null)
    //                         .show();
    //             }
    //         }
    //     };
    //     binding.poll.setOnClickListener(storyActionListener);
    //     binding.answer.setOnClickListener(storyActionListener);
    //     binding.mention.setOnClickListener(storyActionListener);
    //     binding.quiz.setOnClickListener(storyActionListener);
    //     binding.slider.setOnClickListener(storyActionListener);
    // }

    // private void resetView() {
    //     final Context context = getContext();
    //     if (context == null) return;
    //     StoryModel live = null;
    //     slidePos = 0;
    //     lastSlidePos = 0;
    //     if (menuDownload != null) menuDownload.setVisible(false);
    //     if (menuDm != null) menuDm.setVisible(false);
    //     if (menuProfile != null) menuProfile.setVisible(false);
    //     downloadVisible = false;
    //     dmVisible = false;
    //     profileVisible = false;
    //     binding.imageViewer.setController(null);
    //     releasePlayer();
    //     String currentStoryMediaId = null;
    //     final Type type = options.getType();
    //     StoryViewerOptions fetchOptions = null;
    //     switch (type) {
    //         case HIGHLIGHT: {
    //             final HighlightsViewModel highlightsViewModel = (HighlightsViewModel) storiesViewModel;
    //             final List<HighlightModel> models = highlightsViewModel.getList().getValue();
    //             if (models == null || models.isEmpty() || currentFeedStoryIndex >= models.size() || currentFeedStoryIndex < 0) {
    //                 Toast.makeText(context, R.string.downloader_unknown_error, Toast.LENGTH_SHORT).show();
    //                 return;
    //             }
    //             final HighlightModel model = models.get(currentFeedStoryIndex);
    //             currentStoryMediaId = model.getId();
    //             fetchOptions = StoryViewerOptions.forHighlight(model.getId());
    //             highlightTitle = model.getTitle();
    //             break;
    //         }
    //         case FEED_STORY_POSITION: {
    //             final FeedStoriesViewModel feedStoriesViewModel = (FeedStoriesViewModel) storiesViewModel;
    //             final List<FeedStoryModel> models = feedStoriesViewModel.getList().getValue();
    //             if (models == null || currentFeedStoryIndex >= models.size() || currentFeedStoryIndex < 0) return;
    //             final FeedStoryModel model = models.get(currentFeedStoryIndex);
    //             currentStoryMediaId = model.getStoryMediaId();
    //             currentStoryUsername = model.getProfileModel().getUsername();
    //             fetchOptions = StoryViewerOptions.forUser(Long.parseLong(currentStoryMediaId), currentStoryUsername);
    //             if (model.isLive()) {
    //                 live = model.getFirstStoryModel();
    //             }
    //             break;
    //         }
    //         case STORY_ARCHIVE: {
    //             final ArchivesViewModel archivesViewModel = (ArchivesViewModel) storiesViewModel;
    //             final List<HighlightModel> models = archivesViewModel.getList().getValue();
    //             if (models == null || models.isEmpty() || currentFeedStoryIndex >= models.size() || currentFeedStoryIndex < 0) {
    //                 Toast.makeText(context, R.string.downloader_unknown_error, Toast.LENGTH_SHORT).show();
    //                 return;
    //             }
    //             final HighlightModel model = models.get(currentFeedStoryIndex);
    //             currentStoryMediaId = parseStoryMediaId(model.getId());
    //             currentStoryUsername = model.getTitle();
    //             fetchOptions = StoryViewerOptions.forStoryArchive(model.getId());
    //             break;
    //         }
    //     }
    //     if (type == Type.USER) {
    //         currentStoryMediaId = String.valueOf(options.getId());
    //         currentStoryUsername = options.getName();
    //         fetchOptions = StoryViewerOptions.forUser(options.getId(), currentStoryUsername);
    //     }
    //     setTitle(type);
    //     viewModel.getList().setValue(Collections.emptyList());
    //     if (type == Type.STORY) {
    //         storiesService.fetch(options.getId(), new ServiceCallback<StoryModel>() {
    //             @Override
    //             public void onSuccess(final StoryModel storyModel) {
    //                 fetching = false;
    //                 binding.storiesList.setVisibility(View.GONE);
    //                 if (storyModel == null) {
    //                     viewModel.getList().setValue(Collections.emptyList());
    //                     currentStory = null;
    //                     return;
    //                 }
    //                 viewModel.getList().setValue(Collections.singletonList(storyModel));
    //                 currentStory = storyModel;
    //                 refreshStory();
    //             }
    //
    //             @Override
    //             public void onFailure(final Throwable t) {
    //                 Toast.makeText(context, t.getMessage(), Toast.LENGTH_SHORT).show();
    //                 Log.e(TAG, "Error", t);
    //             }
    //         });
    //         return;
    //     }
    //     if (currentStoryMediaId == null) return;
    //     final ServiceCallback<List<StoryModel>> storyCallback = new ServiceCallback<List<StoryModel>>() {
    //         @Override
    //         public void onSuccess(final List<StoryModel> storyModels) {
    //             fetching = false;
    //             if (storyModels == null || storyModels.isEmpty()) {
    //                 viewModel.getList().setValue(Collections.emptyList());
    //                 currentStory = null;
    //                 binding.storiesList.setVisibility(View.GONE);
    //                 return;
    //             }
    //             binding.storiesList.setVisibility((storyModels.size() == 1 && currentFeedStoryIndex == -1) ? View.GONE : View.VISIBLE);
    //             if (currentFeedStoryIndex == -1) {
    //                 binding.btnBackward.setVisibility(View.GONE);
    //                 binding.btnForward.setVisibility(View.GONE);
    //             }
    //             viewModel.getList().setValue(storyModels);
    //             currentStory = storyModels.get(0);
    //             refreshStory();
    //         }
    //
    //         @Override
    //         public void onFailure(final Throwable t) {
    //             Toast.makeText(context, t.getMessage(), Toast.LENGTH_SHORT).show();
    //             Log.e(TAG, "Error", t);
    //         }
    //     };
    //     if (live != null) {
    //         storyCallback.onSuccess(Collections.singletonList(live));
    //         return;
    //     }
    //     storiesService.getUserStory(fetchOptions, storyCallback);
    // }

    // private void setTitle(final Type type) {
    //     final boolean hasUsername = !TextUtils.isEmpty(currentStoryUsername);
    //     if (type == Type.HIGHLIGHT) {
    //         final ActionBar actionBar = fragmentActivity.getSupportActionBar();
    //         if (actionBar != null) {
    //             actionBarTitle = highlightTitle;
    //             actionBar.setTitle(highlightTitle);
    //         }
    //     } else if (hasUsername) {
    //         currentStoryUsername = currentStoryUsername.replace("@", "");
    //         final ActionBar actionBar = fragmentActivity.getSupportActionBar();
    //         if (actionBar != null) {
    //             actionBarTitle = currentStoryUsername;
    //             actionBar.setTitle(currentStoryUsername);
    //         }
    //     }
    // }

    // private synchronized void refreshStory() {
    //     if (binding.storiesList.getVisibility() == View.VISIBLE) {
    //         final List<StoryModel> storyModels = viewModel.getList().getValue();
    //         if (storyModels != null && storyModels.size() > 0) {
    //             StoryModel item = storyModels.get(lastSlidePos);
    //             if (item != null) {
    //                 item.setCurrentSlide(false);
    //                 storiesAdapter.notifyItemChanged(lastSlidePos, item);
    //             }
    //             item = storyModels.get(slidePos);
    //             if (item != null) {
    //                 item.setCurrentSlide(true);
    //                 storiesAdapter.notifyItemChanged(slidePos, item);
    //             }
    //         }
    //     }
    //     lastSlidePos = slidePos;
    //
    //     final MediaItemType itemType = currentStory.getItemType();
    //
    //     url = itemType == MediaItemType.MEDIA_TYPE_IMAGE ? currentStory.getStoryUrl() : currentStory.getVideoUrl();
    //
    //     if (itemType != MediaItemType.MEDIA_TYPE_LIVE) {
    //         final String shortCode = currentStory.getTappableShortCode();
    //         binding.viewStoryPost.setVisibility(shortCode != null ? View.VISIBLE : View.GONE);
    //         binding.viewStoryPost.setTag(shortCode);
    //
    //         final String spotify = currentStory.getSpotify();
    //         binding.spotify.setVisibility(spotify != null ? View.VISIBLE : View.GONE);
    //         binding.spotify.setTag(spotify);
    //
    //         poll = currentStory.getPoll();
    //         binding.poll.setVisibility(poll != null ? View.VISIBLE : View.GONE);
    //         binding.poll.setTag(poll);
    //
    //         question = currentStory.getQuestion();
    //         binding.answer.setVisibility((question != null) ? View.VISIBLE : View.GONE);
    //         binding.answer.setTag(question);
    //
    //         mentions = currentStory.getMentions();
    //         binding.mention.setVisibility((mentions != null && mentions.length > 0) ? View.VISIBLE : View.GONE);
    //         binding.mention.setTag(mentions);
    //
    //         quiz = currentStory.getQuiz();
    //         binding.quiz.setVisibility(quiz != null ? View.VISIBLE : View.GONE);
    //         binding.quiz.setTag(quiz);
    //
    //         slider = currentStory.getSlider();
    //         binding.slider.setVisibility(slider != null ? View.VISIBLE : View.GONE);
    //         binding.slider.setTag(slider);
    //
    //         final SwipeUpModel swipeUp = currentStory.getSwipeUp();
    //         if (swipeUp != null) {
    //             binding.swipeUp.setVisibility(View.VISIBLE);
    //             binding.swipeUp.setText(swipeUp.getText());
    //             binding.swipeUp.setTag(swipeUp.getUrl());
    //         } else binding.swipeUp.setVisibility(View.GONE);
    //     }
    //
    //     releasePlayer();
    //     final Type type = options.getType();
    //     if (type == Type.HASHTAG || type == Type.LOCATION) {
    //         final ActionBar actionBar = fragmentActivity.getSupportActionBar();
    //         if (actionBar != null) {
    //             actionBarTitle = currentStory.getUsername();
    //             actionBar.setTitle(currentStory.getUsername());
    //         }
    //     }
    //     if (itemType == MediaItemType.MEDIA_TYPE_VIDEO) setupVideo();
    //     else if (itemType == MediaItemType.MEDIA_TYPE_LIVE) setupLive();
    //     else setupImage();
    //
    //     final ActionBar actionBar = fragmentActivity.getSupportActionBar();
    //     actionBarSubtitle = Utils.datetimeParser.format(new Date(currentStory.getTimestamp() * 1000L));
    //     if (actionBar != null) {
    //         try {
    //             actionBar.setSubtitle(actionBarSubtitle);
    //         } catch (Exception e) {
    //             Log.e(TAG, "refreshStory: ", e);
    //         }
    //     }
    //
    //     if (settingsHelper.getBoolean(MARK_AS_SEEN))
    //         storiesService.seen(currentStory.getStoryMediaId(),
    //                             currentStory.getTimestamp(),
    //                             System.currentTimeMillis() / 1000,
    //                             null);
    // }

    // private void downloadStory() {
    //     final Context context = getContext();
    //     if (context == null) return;
    //     if (currentStory == null) {
    //         Toast.makeText(context, R.string.downloader_unknown_error, Toast.LENGTH_SHORT).show();
    //         return;
    //     }
    //     DownloadUtils.download(context, currentStory);
    // }

    private void setupImage(@NonNull final StoryMedia storyMedia) {
        binding.progressView.setVisibility(View.VISIBLE);
        binding.playerView.setVisibility(View.GONE);
        binding.imageViewer.setVisibility(View.VISIBLE);
        final String imageUrl = ResponseBodyUtils.getImageUrl(storyMedia);
        final DraweeController controller = Fresco
                .newDraweeControllerBuilder()
                .setUri(imageUrl)
                .setOldController(binding.imageViewer.getController())
                .setControllerListener(new BaseControllerListener<ImageInfo>() {
                    @Override
                    public void onFailure(final String id, final Throwable throwable) {
                        binding.progressView.setVisibility(View.GONE);
                    }

                    @Override
                    public void onFinalImageSet(final String id,
                                                final ImageInfo imageInfo,
                                                final Animatable animatable) {
                        showMenuItems(storyMedia);
                        binding.progressView.setVisibility(View.GONE);
                    }
                })
                .build();
        binding.imageViewer.setController(controller);
    }

    private void setupVideo(@NonNull final StoryMedia storyMedia) {
        binding.playerView.setVisibility(View.VISIBLE);
        binding.progressView.setVisibility(View.GONE);
        binding.imageViewer.setVisibility(View.GONE);
        binding.imageViewer.setController(null);

        final Context context = getContext();
        if (context == null) return;
        player = new SimpleExoPlayer.Builder(context).build();
        binding.playerView.setPlayer(player);
        binding.playerView.setShowNextButton(false);
        binding.playerView.setShowPreviousButton(false);
        binding.playerView.setShowRewindButton(false);
        binding.playerView.setShowFastForwardButton(false);
        player.setPlayWhenReady(settingsHelper.getBoolean(Constants.AUTOPLAY_VIDEOS));

        String url = null;
        final List<VideoVersion> videoVersions = storyMedia.getVideoVersions();
        if (videoVersions != null && !videoVersions.isEmpty()) {
            final VideoVersion videoVersion = videoVersions.get(0);
            url = videoVersion.getUrl();
        }
        if (url == null) return;
        final Uri uri = Uri.parse(url);
        final MediaItem mediaItem = MediaItem.fromUri(uri);
        final MediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(new DefaultDataSourceFactory(context, "instagram"));
        final MediaSource mediaSource = mediaSourceFactory.createMediaSource(mediaItem);
        mediaSource.addEventListener(new Handler(Looper.getMainLooper()), new MediaSourceEventListener() {
            @Override
            public void onLoadStarted(final int windowIndex,
                                      @Nullable final MediaSource.MediaPeriodId mediaPeriodId,
                                      @NonNull final LoadEventInfo loadEventInfo,
                                      @NonNull final MediaLoadData mediaLoadData) {
                showMenuItems(storyMedia);
                binding.progressView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onLoadCompleted(final int windowIndex,
                                        @Nullable final MediaSource.MediaPeriodId mediaPeriodId,
                                        @NonNull final LoadEventInfo loadEventInfo,
                                        @NonNull final MediaLoadData mediaLoadData) {
                showMenuItems(storyMedia);
                binding.progressView.setVisibility(View.GONE);
            }

            @Override
            public void onLoadCanceled(final int windowIndex,
                                       @Nullable final MediaSource.MediaPeriodId mediaPeriodId,
                                       @NonNull final LoadEventInfo loadEventInfo,
                                       @NonNull final MediaLoadData mediaLoadData) {
                binding.progressView.setVisibility(View.GONE);
            }

            @Override
            public void onLoadError(final int windowIndex,
                                    @Nullable final MediaSource.MediaPeriodId mediaPeriodId,
                                    @NonNull final LoadEventInfo loadEventInfo,
                                    @NonNull final MediaLoadData mediaLoadData,
                                    @NonNull final IOException error,
                                    final boolean wasCanceled) {
                hideMenuItems();
                binding.progressView.setVisibility(View.GONE);
            }
        });
        player.setMediaSource(mediaSource);
        player.prepare();
        // binding.playerView.setOnClickListener(v -> {
        //     if (player != null) {
        //         if (player.getPlaybackState() == Player.STATE_ENDED) player.seekTo(0);
        //         player.setPlayWhenReady(player.getPlaybackState() == Player.STATE_ENDED || !player.isPlaying());
        //     }
        // });
    }

    private void setupStickers(@NonNull final StoryMedia storyMedia) {
        binding.stickers.removeAllViews();
        final List<View> stickerViews = new ArrayList<>();
        final List<StoryQuestion> storyQuestions = storyMedia.getStoryQuestions();
        if (storyQuestions != null && !storyQuestions.isEmpty()) {
            final List<View> questionStickers = setupStickers(storyMedia, storyQuestions);
            setupQuestionStickers(questionStickers);
            stickerViews.addAll(questionStickers);
        }
        final List<StoryPoll> storyPolls = storyMedia.getStoryPolls();
        if (storyPolls != null && !storyPolls.isEmpty()) {
            stickerViews.addAll(setupStickers(storyMedia, storyPolls));
        }
        final List<StorySlider> storySliders = storyMedia.getStorySliders();
        if (storySliders != null && !storySliders.isEmpty()) {
            stickerViews.addAll(setupStickers(storyMedia, storySliders));
        }
        for (final View view : stickerViews) {
            binding.stickers.addView(view);
        }
        // if (stickerViews.isEmpty()) return;
        // binding.drawView.setVisibility(View.VISIBLE);
        // binding.drawView.setRects(stickerViews);
        // binding.getRoot().setStickers(stickerViews);
    }

    @NonNull
    private List<View> setupStickers(@NonNull final StoryMedia storyMedia,
                                     @NonNull final List<? extends StorySticker> storyStickers) {
        return storyStickers
                .stream()
                .map(storySticker -> {
                    final Context context = getContext();
                    if (context == null) return null;
                    return StickerFactory.createSticker(
                            context,
                            storySticker,
                            storyMedia,
                            binding.storyContainer.getMeasuredWidth(),
                            binding.storyContainer.getMeasuredHeight()
                    );
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private void setupQuestionStickers(@NonNull final List<View> questionStickerViews) {
        for (final View questionStickerView : questionStickerViews) {
            if (!(questionStickerView instanceof QuestionStickerView)) continue;
            ((QuestionStickerView) questionStickerView).setOnQuestionStickerClickListener(this);
        }
    }

    private void showMenuItems(@NonNull final StoryMedia storyMedia) {
        if (menuDownload != null) {
            downloadVisible = true;
            menuDownload.setVisible(true);
        }
        if (storyMedia.canReply() && menuDm != null) {
            dmVisible = true;
            menuDm.setVisible(true);
        }
        final User user = storyMedia.getUser();
        if (menuProfile != null && user != null && !TextUtils.isEmpty(user.getUsername())) {
            profileVisible = true;
            menuProfile.setVisible(true);
        }
    }

    private void hideMenuItems() {
        if (menuDownload != null) {
            downloadVisible = false;
            menuDownload.setVisible(false);
        }
        if (menuDm != null) {
            dmVisible = false;
            menuDm.setVisible(false);
        }
        if (menuProfile != null) {
            profileVisible = false;
            menuProfile.setVisible(false);
        }
    }

    @Override
    public void onQuestionStickerClick(@NonNull final QuestionStickerView view, @NonNull final StoryQuestion storyQuestion) {
        Log.d(TAG, "onQuestionStickerClick: " + storyQuestion);
        final long questionId = storyQuestion.getQuestionSticker().getQuestionId();
        binding.getRoot().post(() -> {
            TransitionManager.beginDelayedTransition(binding.getRoot());
            binding.stickers.removeView(view);
            binding.activeStickerContainer.addView(view);
            binding.activeStickerContainer.setVisibility(View.VISIBLE);
            final String backgroundColor = storyQuestion.getQuestionSticker().getBackgroundColor();
            final int color = Color.parseColor(backgroundColor);
            binding.activeStickerContainer.setBackgroundColor(ColorUtils.setAlphaComponent(color, 200));
            view.setElevation(Utils.convertDpToPx(8));
            binding.getRoot().post(() -> {
                final ValueAnimator translationX = ObjectAnimator.ofFloat(
                        view,
                        "translationX",
                        (Utils.displayMetrics.widthPixels - view.getMeasuredWidth()) / 2f
                );
                final ValueAnimator translationY = ObjectAnimator.ofFloat(
                        view,
                        "translationY",
                        Utils.displayMetrics.heightPixels * 0.3f
                );
                final ValueAnimator rotation = ObjectAnimator.ofFloat(
                        view,
                        "rotation",
                        0
                );
                final AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playTogether(translationX, translationY, rotation);
                animatorSet.setDuration(500);
                animatorSet.start();
            });
        });
    }

    // @Override
    // public void onStickerClick(@NonNull final StoryStickerRect sticker) {
    //     Log.d(TAG, "onStickerClick: " + sticker);
    // }

    // public static class StoryStickerRect {
    //     private final StorySticker sticker;
    //
    //     public StoryStickerRect(@NonNull final StorySticker sticker) {
    //         this.sticker = sticker;
    //     }
    //
    //     public StorySticker getSticker() {
    //         return sticker;
    //     }
    // }
    // private void setupLive() {
    //     binding.playerView.setVisibility(View.VISIBLE);
    //     binding.progressView.setVisibility(View.GONE);
    //     binding.imageViewer.setVisibility(View.GONE);
    //     binding.imageViewer.setController(null);
    //
    //     if (menuDownload != null) menuDownload.setVisible(false);
    //     if (menuDm != null) menuDm.setVisible(false);
    //
    //     final Context context = getContext();
    //     if (context == null) return;
    //     player = new SimpleExoPlayer.Builder(context).build();
    //     binding.playerView.setPlayer(player);
    //     player.setPlayWhenReady(settingsHelper.getBoolean(Constants.AUTOPLAY_VIDEOS));
    //
    //     final Uri uri = Uri.parse(url);
    //     final MediaItem mediaItem = MediaItem.fromUri(uri);
    //     final DashMediaSource mediaSource = new DashMediaSource.Factory(new DefaultDataSourceFactory(context, "instagram"))
    //             .createMediaSource(mediaItem);
    //     mediaSource.addEventListener(new Handler(), new MediaSourceEventListener() {
    //         @Override
    //         public void onLoadCompleted(final int windowIndex,
    //                                     @Nullable final MediaSource.MediaPeriodId mediaPeriodId,
    //                                     @NonNull final LoadEventInfo loadEventInfo,
    //                                     @NonNull final MediaLoadData mediaLoadData) {
    //             binding.progressView.setVisibility(View.GONE);
    //         }
    //
    //         @Override
    //         public void onLoadStarted(final int windowIndex,
    //                                   @Nullable final MediaSource.MediaPeriodId mediaPeriodId,
    //                                   @NonNull final LoadEventInfo loadEventInfo,
    //                                   @NonNull final MediaLoadData mediaLoadData) {
    //             binding.progressView.setVisibility(View.VISIBLE);
    //         }
    //
    //         @Override
    //         public void onLoadCanceled(final int windowIndex,
    //                                    @Nullable final MediaSource.MediaPeriodId mediaPeriodId,
    //                                    @NonNull final LoadEventInfo loadEventInfo,
    //                                    @NonNull final MediaLoadData mediaLoadData) {
    //             binding.progressView.setVisibility(View.GONE);
    //         }
    //
    //         @Override
    //         public void onLoadError(final int windowIndex,
    //                                 @Nullable final MediaSource.MediaPeriodId mediaPeriodId,
    //                                 @NonNull final LoadEventInfo loadEventInfo,
    //                                 @NonNull final MediaLoadData mediaLoadData,
    //                                 @NonNull final IOException error,
    //                                 final boolean wasCanceled) {
    //             binding.progressView.setVisibility(View.GONE);
    //         }
    //     });
    //     player.setMediaSource(mediaSource);
    //     player.prepare();
    //
    //     binding.playerView.setOnClickListener(v -> {
    //         if (player != null) {
    //             if (player.getPlaybackState() == Player.STATE_ENDED) player.seekTo(0);
    //             player.setPlayWhenReady(player.getPlaybackState() == Player.STATE_ENDED || !player.isPlaying());
    //         }
    //     });
    // }

    // private void openProfile(final String username) {
    //     final ActionBar actionBar = fragmentActivity.getSupportActionBar();
    //     if (actionBar != null) {
    //         actionBar.setSubtitle(null);
    //     }
    //     final char t = username.charAt(0);
    //     if (t == '@') {
    //         final NavDirections action = HashTagFragmentDirections.actionGlobalProfileFragment(username);
    //         NavHostFragment.findNavController(this).navigate(action);
    //     } else if (t == '#') {
    //         final NavDirections action = HashTagFragmentDirections.actionGlobalHashTagFragment(username.substring(1));
    //         NavHostFragment.findNavController(this).navigate(action);
    //     } else {
    //         final NavDirections action = ProfileFragmentDirections
    //                 .actionGlobalLocationFragment(Long.parseLong(username.split(" \\(")[1].replace(")", "")));
    //         NavHostFragment.findNavController(this).navigate(action);
    //     }
    // }

    // private void releasePlayer() {
    //     if (player == null) return;
    //     try { player.stop(true); } catch (Exception ignored) { }
    //     try { player.release(); } catch (Exception ignored) { }
    //     player = null;
    // }

    // private void paginateStories(Object newFeedStory, Object oldFeedStory, Context context, boolean backward, boolean last) {
    //     if (newFeedStory != null) {
    //         if (fetching) {
    //             Toast.makeText(context, R.string.be_patient, Toast.LENGTH_SHORT).show();
    //             return;
    //         }
    //         if (settingsHelper.getBoolean(MARK_AS_SEEN)
    //                 && oldFeedStory instanceof FeedStoryModel
    //                 && storiesViewModel instanceof FeedStoriesViewModel) {
    //             final FeedStoriesViewModel feedStoriesViewModel = (FeedStoriesViewModel) storiesViewModel;
    //             final FeedStoryModel oldFeedStoryModel = (FeedStoryModel) oldFeedStory;
    //             if (!oldFeedStoryModel.isFullyRead()) {
    //                 oldFeedStoryModel.setFullyRead(true);
    //                 final List<FeedStoryModel> models = feedStoriesViewModel.getList().getValue();
    //                 final List<FeedStoryModel> modelsCopy = models == null ? new ArrayList<>() : new ArrayList<>(models);
    //                 modelsCopy.set(currentFeedStoryIndex, oldFeedStoryModel);
    //                 feedStoriesViewModel.getList().postValue(models);
    //             }
    //         }
    //         fetching = true;
    //         binding.btnBackward.setVisibility(currentFeedStoryIndex == 1 && backward ? View.INVISIBLE : View.VISIBLE);
    //         binding.btnForward.setVisibility(last ? View.INVISIBLE : View.VISIBLE);
    //         currentFeedStoryIndex = backward ? (currentFeedStoryIndex - 1) : (currentFeedStoryIndex + 1);
    //         resetView();
    //     }
    // }
}
