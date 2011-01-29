package com.anoshenko.android.mahjongg;

import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

import com.anoshenko.android.toolbar.ToolbarButton;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.Menu;
import android.widget.TextView;

public class PlayActivity extends BaseActivity {

	private final static String STORE_KEY = "STORE";
	private final static String MARKED_KEY = "MARKED";
	private final static String ZOOM_KEY = "ZOOM";
	private final static String AUTOPLAY_KEY = "AUTOPLAY";

	private final static int UNDO_BUTTON	= 0;
	private final static int REDO_BUTTON	= 1;
	private final static int ZOOM_BUTTON	= 2;
	private final static int HINT_BUTTON	= 3;
	private final static int GAME_BUTTON	= 4;
	private final static int BUTTON_COUNT	= 5;

	private final static int HINT_BACK_BUTTON	= 0;
	private final static int HINT_PREV_BUTTON	= 1;
	private final static int HINT_NEXT_BUTTON	= 2;
	private final static int HINT_BUTTON_COUNT	= 3;

	private final static int AUTOPLAY_STOP_BUTTON	= 0;
	private final static int AUTOPLAY_SLOW_BUTTON	= 1;
	private final static int AUTOPLAY_FAST_BUTTON	= 2;
	private final static int AUTOPLAY_BUTTON_COUNT	= 3;

	private final static int PLAY_MODE		= 0;
	private final static int HINT_MODE		= 1;
	private final static int AUTOPLAY_MODE	= 2;

	private final static int MIN_AUTOPLAY_SPEED	= 1;
	private final static int MAX_AUTOPLAY_SPEED	= 6;

	int mTouchArrea = 20;

	private final ToolbarButton[] mToolbarButton = new ToolbarButton[BUTTON_COUNT];
	private final ToolbarButton[] mHintToolbarButton = new ToolbarButton[HINT_BUTTON_COUNT];
	private final ToolbarButton[] mAutoplayToolbarButton = new ToolbarButton[AUTOPLAY_BUTTON_COUNT];

	private MahjonggData mData;
	private GameView mPlayView;
	private MoveMemory mMemory;
	private int mMode;

	private int mAutoplaySpeed = 2;

	private boolean mZoom = false;
	private int mZoomX, mZoomY, mVisibleWidth, mVisibleHeight;
	private int mDownX, mDownY, mPrevX, mPrevY, mDistance;

	private final class Die implements Comparable<Die> {
		final int Value, Layer, Row, Collumn;
		int Distance;

		Die(int value, int layer, int row, int collumn) {
			Value	= value;
			Layer	= layer;
			Row		= row;
			Collumn	= collumn;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Die) {
				Die die = (Die)obj;
				return die.Value == Value && die.Layer == Layer &&
					die.Row == Row && die.Collumn == Collumn;
			}

			return false;
		}

		@Override
		public int compareTo(Die another) {
			return Value - another.Value;
		}
	}

	private Vector<Die> mMarked = new Vector<Die>();
	//private int mMarkedBorder = 2;

	private Vector<Die> mAvailableList = new Vector<Die>();
	private long mStartTime, mCurrentTime;

	private Vector<Die> mHintList = new Vector<Die>();
	private int mHint;

	private int mShuffleLeft;
	private int mAvailableMoves, mDiesLeft;

	//--------------------------------------------------------------------------
	@Override
	protected int getLayoutId() {
		return R.layout.play_view;
	}

	//--------------------------------------------------------------------------
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		DisplayMetrics dm = Utils.getDisplayMetrics(this);
		int min_size = Math.min(dm.heightPixels, dm.widthPixels);

		if (min_size < 320) {
			mTouchArrea = 15;
		} else if (min_size > 320) {
			mTouchArrea = 30;
		} else {
			mTouchArrea = 20;
		}

		mMode = PLAY_MODE;
		mHint = -1;

		if (savedInstanceState != null)
			mZoom = savedInstanceState.getBoolean(ZOOM_KEY);

		int id = getIntent().getIntExtra(GAME_ID_KEY, -1);
		try {
			mData = new MahjonggData(this, id);
		} catch (MahjonggData.LoadExeption e) {
			e.printStackTrace();
			finish();
			return;
		}

		setStartShuffleLeft();

		TextView title = (TextView) findViewById(R.id.PlayTitleText);
		title.setText(mData.getName());

		mData.loadStatistics();

		mLayer = new int[mData.getLayerCount()][mData.getLayerHeight()][mData.getLayerWidth()];
		mMemory = new MoveMemory(this);

		mPlayView = (GameView)findViewById(R.id.PlayArrea);

		Resources res = getResources();

		mToolbarButton[UNDO_BUTTON] = new ToolbarButton(res, Command.UNDO,
				R.string.undo, R.drawable.icon_undo, R.drawable.icon_undo_disable);
		mToolbarButton[UNDO_BUTTON].mEnabled = false;

		mToolbarButton[REDO_BUTTON] = new ToolbarButton(res, Command.REDO,
				R.string.redo, R.drawable.icon_redo, R.drawable.icon_redo_disable);
		mToolbarButton[REDO_BUTTON].mEnabled = false;

		if (mZoom)
			mToolbarButton[ZOOM_BUTTON] = new ToolbarButton(res, Command.ZOOM,
					R.string.zoom_out, R.drawable.icon_zoom_out, -1);
		else
			mToolbarButton[ZOOM_BUTTON] = new ToolbarButton(res, Command.ZOOM,
					R.string.zoom_in, R.drawable.icon_zoom_in, -1);

		mToolbarButton[HINT_BUTTON] = new ToolbarButton(res, Command.HINT,
				R.string.hint, R.drawable.icon_hint, -1);

		mToolbarButton[GAME_BUTTON] = new ToolbarButton(res, Command.GAME_MENU,
				R.string.game, R.drawable.icon_start, -1);

		for (int i=0; i<mToolbarButton.length; i++)
			mToolbarButton[i].loadName(this);

		mHintToolbarButton[HINT_BACK_BUTTON] = new ToolbarButton(res, Command.HINT_BACK,
				R.string.game, R.drawable.icon_start, -1);

		mHintToolbarButton[HINT_PREV_BUTTON] = new ToolbarButton(res, Command.HINT_PREV,
				R.string.prev_button, R.drawable.icon_prev, R.drawable.icon_prev_disable);

		mHintToolbarButton[HINT_NEXT_BUTTON] = new ToolbarButton(res, Command.HINT_NEXT,
				R.string.next_button, R.drawable.icon_next, R.drawable.icon_next_disable);

		for (int i=0; i<mHintToolbarButton.length; i++)
			mHintToolbarButton[i].loadName(this);

		mAutoplayToolbarButton[AUTOPLAY_STOP_BUTTON] = new ToolbarButton(res,
				Command.AUTOPLAY_STOP, R.string.stop_button, R.drawable.icon_stop, -1);

		mAutoplayToolbarButton[AUTOPLAY_SLOW_BUTTON] = new ToolbarButton(res,
				Command.AUTOPLAY_SLOW, R.string.slow_button,
				R.drawable.icon_slow, R.drawable.icon_slow_disable);

		mAutoplayToolbarButton[AUTOPLAY_FAST_BUTTON] = new ToolbarButton(res,
				Command.AUTOPLAY_FAST, R.string.fast_button,
				R.drawable.icon_fast, R.drawable.icon_fast_disable);

		for (int i=0; i<mAutoplayToolbarButton.length; i++)
			mAutoplayToolbarButton[i].loadName(this);

		final int layer_count = mData.getLayerCount();
		final int layer_width = mData.getLayerWidth();
		final int layer_height = mData.getLayerHeight();
		int i, j, k;

		for (i=0; i<layer_count; i++)
			for (j=0; j<layer_height; j++)
				for (k=0; k<layer_width; k++)
					mLayer[i][j][k] = FREE_PLACE;

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		mAutoplaySpeed = prefs.getInt(AUTOPLAY_KEY, 2);

		String stored = getPreferenceValue(STORE_KEY + mData.getId(), null);
		boolean need_start = true;
		if (stored != null) {
			int pos = stored.indexOf('\n');
			String data;

			if (pos >= 0) {
				String line = stored.substring(0, pos);
				int pos2 = line.indexOf(' ');

				if (pos2 > 0) {
					mCurrentTime = Integer.parseInt(line.substring(0, pos2)) * 1000;
					mShuffleLeft = Integer.parseInt(line.substring(pos2+1));
				} else {
					mCurrentTime = Integer.parseInt(line) * 1000;
					setStartShuffleLeft();
				}

				pos++;
				pos2 = stored.indexOf('\n', pos);
				if (pos2 > 0) {
					data = stored.substring(pos, pos2);
					for (i=0; i<data.length() && i<mStartDies.length; i++)
						mStartDies[i] = (int)(data.charAt(i) - 'A');

					pos = pos2 + 1;
					pos2 = stored.indexOf('\n', pos);
					if (pos2 > 0) {
						data = stored.substring(pos, pos2);
						for (i=0; i<data.length(); i+=4) {
							int die = (int)(data.charAt(i) - 'A');
							int layer = (int)(data.charAt(i+1) - 'A');
							int row = (int)(data.charAt(i+2) - 'A');
							int collumn = (int)(data.charAt(i+3) - 'A');

							mLayer[layer][row][collumn] = die;
						}

						need_start = false;
						mMemory.Load(stored.substring(pos2+1));

						if (savedInstanceState != null) {
							data = savedInstanceState.getString(MARKED_KEY);
							if (data != null)
								for (i=0; i<data.length(); i+=4) {
									int die = (int)(data.charAt(i) - 'A');
									int layer = (int)(data.charAt(i+1) - 'A');
									int row = (int)(data.charAt(i+2) - 'A');
									int collumn = (int)(data.charAt(i+3) - 'A');

									mMarked.add(new Die(die, layer, row, collumn));
								}
						}
					}
				}
			}
		}

		if (need_start)
			Start(false);
		else
			ResumeMove();

		switch (mMode) {
		case HINT_MODE:
			startHintMode(mHint);
			break;

		case AUTOPLAY_MODE:
			autoplayStart(0);
			break;

		default:
			mToolbar.setButtons(mToolbarButton);
		}

		updateToolbar();
	}

	//--------------------------------------------------------------------------
	protected void onSaveInstanceState(Bundle outState) {
		if (mMarked.size() > 0) {
			StringBuilder builder = new StringBuilder();

			for (Die die : mMarked) {
				builder.append((char)(die.Value + 'A'));
				builder.append((char)(die.Layer + 'A'));
				builder.append((char)(die.Row + 'A'));
				builder.append((char)(die.Collumn + 'A'));
			}

			outState.putString(MARKED_KEY, builder.toString());
		}

		outState.putBoolean(ZOOM_KEY, mZoom);
	}

	//--------------------------------------------------------------------------
	@Override
	protected void onResume() {
		super.onResume();
		ResumeTime();
	}

	//--------------------------------------------------------------------------
	@Override
	protected void onPause() {
		PauseTime();
		super.onPause();
	}

	//--------------------------------------------------------------------------
	@Override
	protected void onDestroy() {
		if (mData != null) {
			mData.storeStatistics();

			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
			SharedPreferences.Editor editor = prefs.edit();
			String key = STORE_KEY + mData.getId();

			if (!mMemory.isFull()) {
				final int layer_count = mData.getLayerCount();
				final int layer_width = mData.getLayerWidth();
				final int layer_height = mData.getLayerHeight();

				StringBuilder builder = new StringBuilder();
				int i, j, k, n;

				if(!fTimePause) {
					mCurrentTime = System.currentTimeMillis() - mStartTime;
				}
				builder.append(mCurrentTime / 1000);
				builder.append(' ');
				builder.append(mShuffleLeft);
				builder.append("\n");

				for (i=0; i<mStartDies.length; i++)
					builder.append((char)('A' + mStartDies[i]));

				builder.append('\n');

				for (i=0; i<layer_count; i++)
					for (j=0; j<layer_height; j++)
						for (k=0; k<layer_width; k++) {
							n = mLayer[i][j][k];
							if (n >= 0) {
								builder.append((char)('A' + n));
								builder.append((char)('A' + i));
								builder.append((char)('A' + j));
								builder.append((char)('A' + k));
							}
						}

				builder.append('\n');
				mMemory.Store(builder);

				editor.putString(key, builder.toString());
			} else {
				editor.remove(key);
			}

			editor.commit();
		}

		super.onDestroy();
	}

	//--------------------------------------------------------------------------
	private boolean fTimePause = true;
	private TextView mTimeTextView = null;

	private Runnable gameTimeRunnable = new Runnable() {
		@Override
		public void run() {
			if(!fTimePause) {
				if(mTimeTextView == null)
					mTimeTextView = (TextView)findViewById(R.id.PlayTimer);

				if(mTimeTextView != null) {
					int time = (int)((System.currentTimeMillis() - mStartTime) / 1000);
					mTimeTextView.setText(String.format("%d/%d  %du  %ds  %d:%02d",
							mAvailableMoves, mDiesLeft,
							mMemory.getRedoCount(),
							mShuffleLeft,
							time / 60, time % 60));
				}

				long delay = 1000 - (System.currentTimeMillis() - mStartTime) % 1000;
				mHandler.postDelayed(gameTimeRunnable, delay);
			}
		}
	};

	//--------------------------------------------------------------------------
	void ResumeTime() {
		if(fTimePause) {
			mStartTime = System.currentTimeMillis() - mCurrentTime;
			fTimePause = false;
			gameTimeRunnable.run();
		}
	}

	//--------------------------------------------------------------------------
	void PauseTime() {
		if(!fTimePause) {
			fTimePause = true;
			mCurrentTime = System.currentTimeMillis() - mStartTime;
		}
	}

	//--------------------------------------------------------------------------
	void AddPenalty(int penalty) {
		mStartTime -= 1000 * penalty;
		gameTimeRunnable.run();
	}

	//--------------------------------------------------------------------------
	protected void doCommand(int command) {
		switch (command) {
		case Command.UNDO:
			mMemory.Undo();
			break;

		case Command.REDO:
			mMemory.Redo();
			break;

		case Command.TRASH: {
			int[] trash = mMemory.getTrash();
			if (trash == null) {
				Utils.Note(this, R.string.trash_is_empty);
			} else {
				TrashDialog.show(this, trash);
			}
			break;
		}
		case Command.ZOOM:
			mZoom = !mZoom;
			updateZBuffer(mVisibleWidth, mVisibleHeight);

			if (mZoom) {
				mPlayView.setOffset(mZoomX, mZoomY);
				mToolbarButton[ZOOM_BUTTON].setIcon(this, R.drawable.icon_zoom_out);
				mToolbarButton[ZOOM_BUTTON].setText(this, R.string.zoom_out);
			} else {
				mPlayView.setOffset(0, 0);
				mToolbarButton[ZOOM_BUTTON].setIcon(this, R.drawable.icon_zoom_in);
				mToolbarButton[ZOOM_BUTTON].setText(this, R.string.zoom_in);
			}

			mPlayView.invalidate();
			mToolbar.invalidate();
			break;

		case Command.HINT:
			startHintMode(0);
			break;

		case Command.HINT_BACK:
			finishHintMode();
			break;

		case Command.HINT_PREV:
			prevHint();
			break;

		case Command.HINT_NEXT:
			nextHint();
			break;

		case Command.GAME_MENU: {
			PopupMenu menu = new PopupMenu(this, this);
			menu.addItem(Command.PLAY, R.string.start_item, R.drawable.icon_start);
			menu.addItem(Command.RESTART, R.string.restart_item, R.drawable.icon_restart);
			menu.addItem(Command.SHUFFLE, R.string.shuffle_item, R.drawable.icon_shuffle);
			menu.addItem(Command.AUTOPLAY, R.string.autoplay_item, R.drawable.icon_autoplay);
			menu.addItem(Command.SET_BOOKMARK, R.string.bookmark_item, R.drawable.icon_bookmark);
			menu.addItem(Command.BACK_BOOKMARK, R.string.back_bookmark_item, R.drawable.icon_bookmark_back);
			menu.addItem(Command.TRASH, R.string.trash, R.drawable.icon_trash);
			menu.addItem(Command.STATISTICS, R.string.statistics_item, R.drawable.icon_statistics);
			menu.show();
			break;
		}

		case Command.PLAY:
			Utils.Question(this, R.string.start_question, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					mData.increaseLosses();
					mData.storeStatistics();
					Start(true);
				}
			});
			break;

		case Command.RESTART:
			Utils.Question(this, R.string.restart_question, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					Restart();
				}
			});
			break;

		case Command.SHUFFLE:
			if (mShuffleLeft > 0) {
				Utils.Question(this, R.string.shuffle_question, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						shuffle();
					}
				});
			}
			break;

		case Command.SET_BOOKMARK:
			mMemory.setBookmark();
			break;

		case Command.BACK_BOOKMARK:
			if (mMemory.isBookmarkExist())
				mMemory.backToBookmark();
			else
				Utils.Note(this, R.string.no_bookmark_note);
			break;

		case Command.STATISTICS:
			StatisticsDialog.show(PlayActivity.this, mData);
			break;

		case Command.BUILDER:
			setResult(Command.BUILDER);
			finish();
			break;

		case Command.AUTOPLAY:
			if (!autoplayStart(0)) {
				ResumeMove();
			}
			break;

		case Command.AUTOPLAY_STOP:
			autoplayFinish();
			break;

		case Command.AUTOPLAY_SLOW:
			if (mAutoplaySpeed < MAX_AUTOPLAY_SPEED) {
				setAutoplaySpeed(mAutoplaySpeed + 1);
			}
			break;

		case Command.AUTOPLAY_FAST:
			if (mAutoplaySpeed > MIN_AUTOPLAY_SPEED) {
				setAutoplaySpeed(mAutoplaySpeed - 1);
			}
			break;

		default:
			super.doCommand(command);
		}
	}

	//--------------------------------------------------------------------------
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		Utils.createMenu(this, menu, true);
		return true;
	}

	//--------------------------------------------------------------------------
	void EnableRedoCommand(boolean state) {
		if (mToolbarButton[REDO_BUTTON].mEnabled != state) {
			mToolbarButton[REDO_BUTTON].mEnabled = state;
			mToolbar.invalidate();
		}
	}

	//--------------------------------------------------------------------------
	void EnableUndoCommand(boolean state) {
		if (mToolbarButton[UNDO_BUTTON].mEnabled != state) {
			mToolbarButton[UNDO_BUTTON].mEnabled = state;
			mToolbar.invalidate();
		}
	}

	//--------------------------------------------------------------------------
	void ResumeMove() {
		RedrawZBuffer();
		mPlayView.invalidate();

		if (mMemory.isFull()) {
			if (mMode == AUTOPLAY_MODE)
				autoplayFinish();

			mData.increaseWins();
			PauseTime();
			mData.updateBestTime((int)(mCurrentTime / 1000));
			mData.storeStatistics();

			StatisticsDialog.show(PlayActivity.this, mData, 0,
					new Runnable() {
						@Override
						public void run() {
							Start(true);
						}},
					new Runnable() {
						@Override
						public void run() {
							finish();
						}});
			return;
		}

		updateAvailableList();
		int count = mAvailableList.size();
		int oldmAvailableMoves = mAvailableMoves;
		mAvailableMoves = 0;
		for (int i=0; i<count-1; i++)
			if (mAvailableList.elementAt(i).Value == mAvailableList.elementAt(i+1).Value) {
				mAvailableMoves++;
			}
		if (mAvailableMoves != oldmAvailableMoves)
			gameTimeRunnable.run();

		if (mAvailableMoves == 0) {
			if (mMode == AUTOPLAY_MODE)
				autoplayFinish();

			if (mShuffleLeft > 0) {
				shuffleQuestion();
			} else {
				noMoveLeftQuestion();
			}
		}
	}

	//--------------------------------------------------------------------------
	private void shuffleQuestion() {
		Utils.Question(this, R.string.shuffle_question_no_moves, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				shuffle();
			}
		});
	}

	//--------------------------------------------------------------------------
	private void noMoveLeftQuestion() {
		Utils.Question(this, R.string.no_moves_start_question, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				mData.increaseLosses();
				mData.storeStatistics();
				Start(true);
			}
		});
	}

	//--------------------------------------------------------------------------
	void Unmark() {
		if (mMarked.size() > 0) {
			mMarked.clear();
			RedrawZBuffer();
			mPlayView.invalidate();
		}
	}

	//--------------------------------------------------------------------------
	void Start(boolean redraw) {
		mToolbarButton[UNDO_BUTTON].mEnabled = false;
		mToolbarButton[REDO_BUTTON].mEnabled = false;
		mToolbar.invalidate();

		mMemory.Reset();
		setStartShuffleLeft();

		Utils.Deal(mStartDies);

		final int layer_count = mData.getLayerCount();
		final int layer_width = mData.getLayerWidth();
		final int layer_height = mData.getLayerHeight();
		int i, j, k, n = 0;

		for (i=0; i<layer_count; i++)
			for (j=0; j<layer_height; j++)
				for (k=0; k<layer_width; k++)
					if (mData.isPlace(i, j, k) && n < mStartDies.length) {
						mLayer[i][j][k] = mStartDies[n];
						n++;
					} else {
						mLayer[i][j][k] = FREE_PLACE;
					}

		updateAvailableList();

		mStartTime = System.currentTimeMillis();
		mCurrentTime = 0;

		if (redraw) {
			ResumeMove();
			ResumeTime();
		} else {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					ResumeMove();
				}
			});
		}
	}

	//--------------------------------------------------------------------------
	void Restart() {
		mToolbarButton[UNDO_BUTTON].mEnabled = false;
		mToolbarButton[REDO_BUTTON].mEnabled = false;
		mToolbar.invalidate();

		mMemory.Reset();
		setStartShuffleLeft();

		final int layer_count = mData.getLayerCount();
		final int layer_width = mData.getLayerWidth();
		final int layer_height = mData.getLayerHeight();
		int i, j, k, n = 0;

		for (i=0; i<layer_count; i++)
			for (j=0; j<layer_height; j++)
				for (k=0; k<layer_width; k++)
					if (mData.isPlace(i, j, k) && n < mStartDies.length) {
						mLayer[i][j][k] = mStartDies[n];
						n++;
					} else {
						mLayer[i][j][k] = FREE_PLACE;
					}

		ResumeTime();
		ResumeMove();
	}

	//--------------------------------------------------------------------------
	@Override
	boolean updateZBuffer(int width, int height) {

		int arrea_width, arrea_height;

		if (mZoom) {
			int zoom_ratio = Integer.parseInt(getPreferenceValue(R.string.pref_zoom_key, "0"));

			switch (zoom_ratio) {
			case 1:
				arrea_width = width * 2;
				arrea_height = height * 2;
				break;

			case 2:
				arrea_width = width * 5 / 2;
				arrea_height = height * 5 / 2;
				break;

			default:
				arrea_width = width * 3 / 2;
				arrea_height = height * 3 / 2;
			}
		} else {
			arrea_width = width;
			arrea_height = height;
		}

		mVisibleWidth = width;
		mVisibleHeight = height;

		if (updateZBuffer(arrea_width, arrea_height, width, height)) {
			if (mZoom) {
				if (mZoomX + width > arrea_width)
					mZoomX = arrea_width - width;

				if (mZoomY + height > arrea_height)
					mZoomY = arrea_height - height;
			}

			mPlayView.setZBuffer(mZBuffer);
			return true;
		}

		return false;
	}

	//--------------------------------------------------------------------------
	private boolean isAvailable(int layer, int row, int collumn) {

		final int layer_count	= mData.getLayerCount();
		final int layer_width	= mData.getLayerWidth();
		final int layer_height	= mData.getLayerHeight();
		int x1 = collumn > 0 ? collumn - 1 : collumn;
		int y1 = row > 0 ? row - 1 : row;
		int x2 = collumn < layer_width - 1 ? collumn + 1 : collumn;
		int y2 = row < layer_height - 1 ? row + 1 : row;

		int i, j, k;

		for (i = layer + 1; i < layer_count; i++)
			for (j = y1; j <= y2; j++)
				for (k = x1; k <= x2; k++)
					if (mLayer[i][j][k] >= 0) {
						return false;
					}

		boolean free = true;

		if (collumn >= 2) {
			if ((mLayer[layer][row][collumn - 2] >= 0)
					|| ((row > 0) && (mLayer[layer][row - 1][collumn - 2] >= 0))
					|| ((row < layer_height - 2) && (mLayer[layer][row + 1][collumn - 2] >= 0))) {
				free = false;
			}
		}

		if (free)
			return true;

		free = true;

		if (collumn <= layer_width - 4) {
			if ((mLayer[layer][row][collumn + 2] >= 0)
					|| ((row > 0) && (mLayer[layer][row - 1][collumn + 2] >= 0))
					|| ((row < layer_height - 2) && (mLayer[layer][row + 1][collumn + 2] >= 0))) {
				free = false;
			}
		}

		return free;
	}

	//--------------------------------------------------------------------------
	private void updateAvailableList() {
		mAvailableList.clear();

		final int layer_count = mData.getLayerCount();
		final int layer_width = mData.getLayerWidth();
		final int layer_height = mData.getLayerHeight();
		int i, j, k;

		mDiesLeft = 0;
		for (i=0; i<layer_count; i++)
			for (j=0; j<layer_height; j++)
				for (k=0; k<layer_width; k++)
					if (mLayer[i][j][k] >= 0) {
						mDiesLeft++;
						if (isAvailable(i, j, k)) {
							mAvailableList.add(new Die(mLayer[i][j][k], i, j, k));
						}
					}

		Collections.sort(mAvailableList);
	}

	//--------------------------------------------------------------------------
	/*private boolean isInAvailableList(int value, int layer, int row, int collumn) {
		for (Die die : mAvailableList)
			if (die.Value == value && die.Layer == layer && die.Row == row && die.Collumn == collumn)
				return true;

		return false;
	}*/

	//--------------------------------------------------------------------------
	private void getLayerTouchList(int x, int y, Vector<Die> result,
			int layer_number, int touch_arrea) {
		final int layer_width = mData.getLayerWidth();
		final int layer_height = mData.getLayerHeight();
		final int cell_width = mDieWidth / 2;
		final int cell_height = mDieHeight / 2;

		int[][] layer = mLayer[layer_number];
		final int x_layer_off = mXOffset + mSideWallSize * layer_number;
		final int y_layer_off = mYOffset - mSideWallSize * layer_number;

		x -= x_layer_off;
		y -= y_layer_off;

		int collumn1 = Math.max(0, (x - touch_arrea - cell_width) / cell_width);
		int collumn2 = Math.min(layer_width - 1, (x + touch_arrea + cell_width - 1) / cell_width);
		int row1 = Math.max(0, (y - touch_arrea - cell_height) / cell_height);
		int row2 = Math.min(layer_height - 1, (y + touch_arrea + cell_height - 1) / cell_height);
		Die die;
		int dx, dy;

		for (int i=row1; i<=row2; i++)
			for (int k=collumn1; k<=collumn2; k++)
				if (layer[i][k] >= 0 && isAvailable(layer_number, i, k)) {
					die = new Die(layer[i][k], layer_number, i, k);
					result.add(die);

					dx = k * cell_width;
					if (x < dx)
						dx = dx - x;
					else if (x > dx + mDieWidth - 1)
						dx = x - dx + mDieWidth - 1;
					else
						dx = 0;

					dy = i * cell_height;
					if (y < dy)
						dy = dy - y;
					else if (y > dy + mDieHeight - 1)
						dy = y - dy + mDieHeight - 1;
					else
						dy = 0;

					die.Distance = Math.max(dx, dy);
				}
	}

	//--------------------------------------------------------------------------
	private Vector<Die> getTouchList(int x, int y) {
		Vector<Die> result = new Vector<Die>();
		final int layer_count = mData.getLayerCount();

		for (int i=0; i<layer_count; i++)
			getLayerTouchList(x, y, result, i, mTouchArrea);

		if (result.size() == 0)
			for (int i=0; i<layer_count; i++)
				getLayerTouchList(x, y, result, i, mTouchArrea*2);

		Collections.sort(result, new Comparator<Die>() {
			@Override
			public int compare(Die die1, Die die2) {
				return die1.Distance - die2.Distance;
			}});

		return result;
	}

	//--------------------------------------------------------------------------
	private void clickAction(int x, int y) {
		Vector<Die> list = getTouchList(x, y);
		if (list.size() > 0) {
			int marked_count = mMarked.size();
			Die disabled = null;
			
			if (marked_count > 0) {
				for (Die marked : mMarked)
					for (Die die : list)
						if (!die.equals(marked) && die.Value == marked.Value) {
							mLayer[marked.Layer][marked.Row][marked.Collumn] = FREE_PLACE;
							mLayer[die.Layer][die.Row][die.Collumn] = FREE_PLACE;
							mMemory.add(die.Value, die.Layer, die.Collumn, die.Row,
									marked.Value, marked.Layer, marked.Collumn, marked.Row);
							mMarked.clear();
							ResumeMove();
							return;
						}

				disabled = mMarked.get(0); 
				Unmark();
			}

			m: for (Die die : list)
				for (Die available : mAvailableList)
					if (disabled == null || disabled.Collumn != die.Collumn ||
						disabled.Row != die.Row || disabled.Layer != die.Layer)
						if (!die.equals(available) && die.Value == available.Value) {
							mMarked.add(die);
							break m;
						}

			if (mMarked.size() == 0) {
				mMarked.add(list.firstElement());
			}

			RedrawZBuffer();
			mPlayView.invalidate();
			
		} else {
			
			Unmark();
		}
	}

	//--------------------------------------------------------------------------
	@Override
	void PenDown(int x, int y) {
		if (mZoom) {
			mDownX = mPrevX = x;
			mDownY = mPrevY = y;
			mDistance = 0;
		} else if (mMode == PLAY_MODE){
			clickAction(x, y);
		}
	}

	//--------------------------------------------------------------------------
	private final static int SCROLL_THRESHOLD = 8;

	@Override
	void PenMove(int x, int y) {
		if (mZoom) {
			int dx = mPrevX - x;
			int dy = mPrevY - y;
			int prev_distance = mDistance;

			mDistance += Math.abs(dx) + Math.abs(dy);

			if (mDistance > SCROLL_THRESHOLD) {
				if (prev_distance <= SCROLL_THRESHOLD) {
					mZoomX += mDownX - x;
					mZoomY += mDownY - y;
				} else {
					mZoomX += dx;
					mZoomY += dy;
				}

				if (mZoomX < 0)
					mZoomX = 0;
				else if (mZoomX + mVisibleWidth > mZBuffer.getWidth())
					mZoomX = mZBuffer.getWidth() - mVisibleWidth;

				if (mZoomY < 0)
					mZoomY = 0;
				else if (mZoomY + mVisibleHeight > mZBuffer.getHeight())
					mZoomY = mZBuffer.getHeight() - mVisibleHeight;

				mPlayView.setOffset(mZoomX, mZoomY);
				mPlayView.invalidate();
			}

			mPrevX = x;
			mPrevY = y;
		}
	}

	//--------------------------------------------------------------------------
	@Override
	void PenUp(int x, int y) {
		if (mMode == PLAY_MODE && mZoom && mDistance <= SCROLL_THRESHOLD) {
			clickAction(x + mZoomX, y + mZoomY);
		}
	}

	//--------------------------------------------------------------------------
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK &&
			event.getAction() == KeyEvent.ACTION_DOWN) {

			switch (mMode) {
			case HINT_MODE:
				finishHintMode();
				return true;

			case AUTOPLAY_MODE:
				autoplayFinish();
				return true;
			}
		}

		return super.onKeyDown(keyCode, event);
	}

	//--------------------------------------------------------------------------
	@Override
	boolean KeyDown(int keyCode, KeyEvent event) {
		// TODO
		return false;
	}

	//--------------------------------------------------------------------------
	@Override
	boolean KeyUp(int keyCode, KeyEvent event) {
		// TODO
		return false;
	}

	//--------------------------------------------------------------------------
	private boolean hasNextHint() {
		int die = mHintList.get(mHint).Value;

		for(int i=mHint+1; i<mHintList.size(); i++)
			if (die != mHintList.get(i).Value)
				return true;

		return false;
	}

	//--------------------------------------------------------------------------
	private void setCurrentHint(int hint) {
		mHint = hint;

		mHintToolbarButton[HINT_PREV_BUTTON].mEnabled = (mHint > 0);
		mHintToolbarButton[HINT_NEXT_BUTTON].mEnabled = hasNextHint();

		mMarked.clear();

		int die = mHintList.get(hint).Value;
		mMarked.add(mHintList.get(hint));

		for (int i=hint+1; i<mHintList.size(); i++)
			if (die == mHintList.get(i).Value)
				mMarked.add(mHintList.get(i));
			else
				break;

		if (mZBuffer != null) {
			RedrawZBuffer();
			mToolbar.invalidate();
			mPlayView.invalidate();
		}
	}

	//--------------------------------------------------------------------------
	private void createHintList() {
		int die, count = mAvailableList.size();
		int[] trash = mMemory.getTrash();
		boolean in_trash;

		mHintList.clear();

		for (int i=0; i<count-1; i++) {
			die = mAvailableList.elementAt(i).Value;
			if (die == mAvailableList.elementAt(i+1).Value) {
				in_trash = false;
				if (trash != null)
					for (int k=0; k<trash.length; k++) {
						if (trash[k] == die) {
							in_trash = true;
							break;
						} else if (trash[k] > die) {
							break;
						}
					}

				if (in_trash) {
					mHintList.insertElementAt(mAvailableList.elementAt(i), 0);
					i++;
					mHintList.insertElementAt(mAvailableList.elementAt(i), 0);
				} else if (i+3 < count &&
						die == mAvailableList.elementAt(i+2).Value &&
						die == mAvailableList.elementAt(i+3).Value) {
					mHintList.insertElementAt(mAvailableList.elementAt(i), 0);
					for (int k=0; k<3; k++) {
						i++;
						mHintList.insertElementAt(mAvailableList.elementAt(i), 0);
					}
				} else {
					mHintList.add(mAvailableList.elementAt(i));
					i++;
					mHintList.add(mAvailableList.elementAt(i));

					while (i < count-1 && die == mAvailableList.elementAt(i+1).Value) {
						i++;
						mHintList.add(mAvailableList.elementAt(i));
					}
				}
			}
		}
	}

	//--------------------------------------------------------------------------
	private void startHintMode(int hint) {
		createHintList();

		if (mHintList.size() == 0) {
			if (mShuffleLeft > 0) {
				shuffleQuestion();
			} else {
				noMoveLeftQuestion();
			}
			return;
		}

		mToolbar.setButtons(mHintToolbarButton);

		int count = mHintList.size(), die;

		if (mMarked.size() > 0) {
			for (Die d : mMarked)
				for (Die dh : mHintList)
					if (d.equals(dh)) {
						die = d.Value;
						for (int i=1; i<count; i++)
							if (mHintList.get(i).Value == die) {
								Die dm = mHintList.remove(i);
								mHintList.insertElementAt(dm, 0);
							}
						break;
					}
		}

		mMode = HINT_MODE;
		setCurrentHint(hint);
	}

	//--------------------------------------------------------------------------
	private void finishHintMode() {
		mMarked.clear();
		mHintList.clear();
		mHint = -1;
		mMode = PLAY_MODE;
		mToolbar.setButtons(mToolbarButton);

		RedrawZBuffer();
		mToolbar.invalidate();
		mPlayView.invalidate();
	}

	//--------------------------------------------------------------------------
	private void prevHint() {
		if (mMode != HINT_MODE)
			return;

		int die = mHintList.get(mHint-1).Value;

		for (int i=mHint-2; i>=0; i--)
			if (die != mHintList.get(i).Value) {
				setCurrentHint(i+1);
				break;
			} else if (i == 0) {
				setCurrentHint(i);
				break;
			}
	}

	//--------------------------------------------------------------------------
	private void nextHint() {
		if (mMode != HINT_MODE)
			return;

		int die = mHintList.get(mHint).Value;

		for (int i=mHint+1; i<mHintList.size(); i++)
			if (die != mHintList.get(i).Value) {
				setCurrentHint(i);
				AddPenalty(30);
				break;
			}
	}

	//--------------------------------------------------------------------------
	@Override
	protected ToolbarButton[] getToolbarButtons() {
		switch (mMode) {
		case HINT_MODE:
			return mHintToolbarButton;

		case AUTOPLAY_MODE:
			return mAutoplayToolbarButton;

		default:
			return mToolbarButton;
		}
	}

	//--------------------------------------------------------------------------
	@Override
	protected int getToolbarId() {
		return R.id.PlayToolbar;
	}

	//--------------------------------------------------------------------------
	@Override
	protected int getToolbarRightId() {
		return R.id.PlayToolbarRight;
	}

	//--------------------------------------------------------------------------
	@Override
	protected void invalidateArrea() {
		mPlayView.invalidate();
	}

	//--------------------------------------------------------------------------
	@Override
	int getLayerHeight() {
		return mData.getLayerHeight();
	}

	//--------------------------------------------------------------------------
	@Override
	int getLayerWidth() {
		return mData.getLayerWidth();
	}

	//--------------------------------------------------------------------------
	@Override
	int getLeftLayerCount() {
		return mData.getLeftLayerCount();
	}

	//--------------------------------------------------------------------------
	@Override
	int getTopLayerCount() {
		return mData.getTopLayerCount();
	}

	//--------------------------------------------------------------------------
	@Override
	int getLayerCount() {
		return mData.getLayerCount();
	}

	//--------------------------------------------------------------------------
	@Override
	protected boolean isLayerVisible(int layer) {
		return true;
	}

	//--------------------------------------------------------------------------
	@Override
	protected boolean isMarked(int layer, int row, int collumn) {
		for (Die die : mMarked)
			if (die.Layer == layer && die.Row == row && die.Collumn == collumn)
				return true;

		return false;
	}

	//--------------------------------------------------------------------------
	@Override
	protected void startLayerDraw(Canvas g, int layer) {
	}

	//--------------------------------------------------------------------------
	private void setAutoplaySpeed(int speed) {
		boolean redraw_toolbar = false;
		int prev_speed = mAutoplaySpeed;

		mAutoplaySpeed = speed;

		if (mAutoplaySpeed <= MIN_AUTOPLAY_SPEED) {
			if (mAutoplaySpeed < MIN_AUTOPLAY_SPEED)
				mAutoplaySpeed = MIN_AUTOPLAY_SPEED;

			if (mAutoplayToolbarButton[AUTOPLAY_FAST_BUTTON].mEnabled) {
				mAutoplayToolbarButton[AUTOPLAY_FAST_BUTTON].mEnabled = false;
				redraw_toolbar = true;
			}
		} else if (!mAutoplayToolbarButton[AUTOPLAY_FAST_BUTTON].mEnabled) {
			mAutoplayToolbarButton[AUTOPLAY_FAST_BUTTON].mEnabled = true;
			redraw_toolbar = true;
		}

		if (mAutoplaySpeed >= MAX_AUTOPLAY_SPEED) {
			if (mAutoplaySpeed > MAX_AUTOPLAY_SPEED)
				mAutoplaySpeed = MAX_AUTOPLAY_SPEED;

			if (mAutoplayToolbarButton[AUTOPLAY_SLOW_BUTTON].mEnabled) {
				mAutoplayToolbarButton[AUTOPLAY_SLOW_BUTTON].mEnabled = false;
				redraw_toolbar = true;
			}
		} else if (!mAutoplayToolbarButton[AUTOPLAY_SLOW_BUTTON].mEnabled) {
			mAutoplayToolbarButton[AUTOPLAY_SLOW_BUTTON].mEnabled = true;
			redraw_toolbar = true;
		}

		if (redraw_toolbar) {
			mToolbar.invalidate();
		}

		if (prev_speed != mAutoplaySpeed) {
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
			SharedPreferences.Editor editor = prefs.edit();

			editor.putInt(AUTOPLAY_KEY, mAutoplaySpeed);
			editor.commit();
		}
	}

	//--------------------------------------------------------------------------
	private boolean autoplayStart(long delay) {
		createHintList();
		if (mHintList.size() > 1) {
			if (mMode != AUTOPLAY_MODE) {
				mMode = AUTOPLAY_MODE;

				mToolbar.setButtons(mAutoplayToolbarButton);
				setAutoplaySpeed(mAutoplaySpeed);
			}

			mMarked.clear();
			mMarked.add(mHintList.get(0));
			mMarked.add(mHintList.get(1));

			RedrawZBuffer();
			mPlayView.invalidate();

			if (delay <= 0)
				mHandler.post(mAutoplay);
			else
				mHandler.postDelayed(mAutoplay, delay);

			return true;

		} else if (mMode == AUTOPLAY_MODE) {
			autoplayFinish();
		}

		return false;
	}

	//--------------------------------------------------------------------------
	private void autoplayFinish() {
		mMode = PLAY_MODE;
		mMarked.clear();
		RedrawZBuffer();
		mPlayView.invalidate();
		mToolbar.setButtons(mToolbarButton);
	}

	//--------------------------------------------------------------------------
	private Runnable mAutoplay = new Runnable() {
		@Override
		public void run() {
			if (mMode != AUTOPLAY_MODE)
				return;

			long time = System.currentTimeMillis() + mAutoplaySpeed * 500;

			if (mHintList.size() > 1) {
				Die die0 = mHintList.get(0);
				Die die1 = mHintList.get(1);

				mLayer[die0.Layer][die0.Row][die0.Collumn] = FREE_PLACE;
				mLayer[die1.Layer][die1.Row][die1.Collumn] = FREE_PLACE;
				mMemory.add(die0.Value, die0.Layer, die0.Collumn, die0.Row,
						die0.Value, die0.Layer, die0.Collumn, die0.Row);
				mMarked.clear();
				ResumeMove();

				if (mMode == AUTOPLAY_MODE) {
					autoplayStart(time - System.currentTimeMillis());
				}
			} else {
				autoplayFinish();
			}
		}
	};

	//--------------------------------------------------------------------------
	private void setStartShuffleLeft() {
		mShuffleLeft = Integer.parseInt(getPreferenceValue(R.string.pref_shuffle_key, "1"));
	}

	//--------------------------------------------------------------------------
	private void shuffle() {
		final int layer_count = mData.getLayerCount();
		final int layer_width = mData.getLayerWidth();
		final int layer_height = mData.getLayerHeight();
		int[] dies = new int[144];
		int count = 0, i, j, k, n;

		AddPenalty(600);

		for (i=0; i<layer_count; i++)
			for (j=0; j<layer_height; j++)
				for (k=0; k<layer_width; k++)
					if (mLayer[i][j][k] >= 0) {
						dies[count] = mLayer[i][j][k];
						count++;
					}

		int[] new_dies = new int[count];

		for (j=count, k=0; j>1; j--, k++) {
			n = (int) (Math.random() * j);
			if (n >= j)
				n = j-1;

			new_dies[k] = dies[n];

			for (i=n+1; i<j; i++)
				dies[i-1] = dies[i];
		}

		new_dies[k] = dies[0];
		n = 0;

		for (i=0; i<layer_count; i++)
			for (j=0; j<layer_height; j++)
				for (k=0; k<layer_width; k++)
					if (mLayer[i][j][k] >= 0) {
						mLayer[i][j][k] = new_dies[n];
						n++;
					}

		if (mShuffleLeft < 4) // 4 means unlimited
			mShuffleLeft--;	
		ResumeMove();
	}
}
