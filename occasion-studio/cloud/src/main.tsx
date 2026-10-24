import React, { useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './style.css';
import { photoLayout } from '../photo-layout.mjs';
import { EFFECTS } from '../effects.mjs';
import { Motion } from './EffectMotion';

type Asset = { id: string; bytes: number; effect?: string; quality?: ReturnType<typeof photoLayout> };
type Occasion = {
  id?: string;
  title: string;
  message: string;
  template: string;
  color: string;
  effect: string;
  mode: string;
  status: string;
  startsAt: number;
  endsAt: number;
  intervalSeconds: number;
  assets: Asset[];
  purgedAt?: number;
};
type Device = {
  id: string;
  name: string;
  revoked: number;
  seen: number | null;
  ack: { state: string; at: number; revision: number } | null;
};
type Template = { label: string; color: string; effect: string; message: string };
type State = {
  events: Occasion[];
  devices: Device[];
  templates: Record<string, Template>;
  revision: number;
  serverTime: number;
};
const emptyState: State = { events: [], devices: [], templates: {}, revision: 0, serverTime: Date.now() };
const draft = (): Occasion => ({
  title: 'A little celebration',
  message: 'Something worth celebrating',
  template: 'custom',
  color: '#b9cebe',
  effect: 'sparkles',
  mode: 'fixed',
  status: 'draft',
  startsAt: Math.ceil(Date.now() / 60000) * 60000,
  endsAt: Math.ceil(Date.now() / 60000) * 60000 + 6 * 3600000,
  intervalSeconds: 90,
  assets: [],
});
const date = (time: number) =>
  new Intl.DateTimeFormat('en-IN', {
    timeZone: 'Asia/Kolkata',
    day: 'numeric',
    month: 'short',
    hour: 'numeric',
    minute: '2-digit',
  }).format(time);
const inputDate = (time: number) => new Date(time + 330 * 60000).toISOString().slice(0, 16);
const fromInput = (value: string) => Date.parse(value + ':00+05:30');
async function api(path: string, method = 'GET', data?: unknown, binary = false) {
  const response = await fetch('/api' + path, {
    method,
    credentials: 'same-origin',
    headers:
      data === undefined ? {} : { 'Content-Type': binary ? 'application/octet-stream' : 'application/json' },
    body: data === undefined ? undefined : binary ? (data as Blob) : JSON.stringify(data),
  });
  const result = await response
    .json()
    .catch(() => ({ error: 'Studio unavailable. Check your internet connection and try again.' }));
  if (response.status === 401 && path !== '/login') window.dispatchEvent(new Event('studio-session-expired'));
  if (!response.ok) throw new Error(result.error ?? 'Could not reach the studio.');
  return result;
}
function Icon({ name, size = 20 }: { name: string; size?: number }) {
  const paths: Record<string, React.ReactNode> = {
    plus: <path d="M12 5v14M5 12h14" />,
    close: <path d="m6 6 12 12M6 18 18 6" />,
    tv: (
      <>
        <rect x="3" y="4" width="18" height="13" rx="3" />
        <path d="M8 21h8M12 17v4" />
      </>
    ),
    sparkle: (
      <>
        <path d="m12 3 2.7 6.3L21 12l-6.3 2.7L12 21l-2.7-6.3L3 12l6.3-2.7Z" />
        <path d="m20 2 .5 1.5L22 4l-1.5.5L20 6l-.5-1.5L18 4l1.5-.5Z" />
      </>
    ),
    photo: (
      <>
        <rect x="3" y="3" width="18" height="18" rx="4" />
        <circle cx="8" cy="8" r="1" />
        <path d="m3 17 5-5 4 4 4-6 5 7" />
      </>
    ),
    clock: (
      <>
        <circle cx="12" cy="12" r="9" />
        <path d="M12 7v5l3 2" />
      </>
    ),
    arrow: <path d="M5 12h14m-5-5 5 5-5 5" />,
    check: <path d="m5 12 4 4L19 6" />,
    lock: (
      <>
        <rect x="5" y="10" width="14" height="11" rx="3" />
        <path d="M8 10V7a4 4 0 0 1 8 0v3" />
      </>
    ),
    leaf: (
      <>
        <path d="M20 4C9 2 2 8 5 16s17 3 15-12Z" />
        <path d="m4 21 12-12" />
      </>
    ),
  };
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {paths[name] ?? paths.sparkle}
    </svg>
  );
}
function App() {
  const [state, setState] = useState<State>(emptyState),
    [signedIn, setSignedIn] = useState<boolean | null>(null);
  const [selected, setSelected] = useState<Occasion>(draft),
    [busy, setBusy] = useState(false),
    [notice, setNotice] = useState('');
  const [password, setPassword] = useState(''),
    [pair, setPair] = useState(false),
    [code, setCode] = useState('');
  const [upload, setUpload] = useState<{
      file: File;
      url: string;
      width?: number;
      height?: number;
      previewError?: boolean;
      needsName?: boolean;
    } | null>(null),
    [focus, setFocus] = useState({ x: 0.5, y: 0.5 });
  const [previewTab, setPreviewTab] = useState('home'),
    [previewIndex, setPreviewIndex] = useState(0),
    [now, setNow] = useState(Date.now());
  const fileInput = useRef<HTMLInputElement>(null);
  const load = async () => {
    const value = await api('/state');
    setState(value);
    setSignedIn(true);
    return value as State;
  };
  useEffect(() => {
    load().catch(() => setSignedIn(false));
  }, []);
  useEffect(() => {
    const expire = () => {
      setSignedIn(false);
      setState(emptyState);
      setSelected(draft());
    };
    window.addEventListener('studio-session-expired', expire);
    return () => window.removeEventListener('studio-session-expired', expire);
  }, []);
  useEffect(() => {
    const finished = state.events.find((e) => e.id === selected.id && e.status === 'ended');
    if (finished && selected.status !== 'ended') setSelected(finished);
  }, [state.events, selected.id, selected.status]);
  useEffect(() => {
    if (!signedIn) return;
    const timer = setInterval(() => {
      load().catch(() => {});
      setNow(Date.now());
    }, 5000);
    return () => clearInterval(timer);
  }, [signedIn]);
  useEffect(() => {
    const url = upload?.url;
    return () => {
      if (url) URL.revokeObjectURL(url);
    };
  }, [upload?.url]);
  useEffect(() => {
    setPreviewIndex(0);
  }, [selected.id]);
  useEffect(() => {
    if (selected.mode !== 'album' || selected.assets.length < 2) return;
    const timer = setInterval(
      () => setPreviewIndex((i) => (i + 1) % selected.assets.length),
      selected.intervalSeconds * 1000,
    );
    return () => clearInterval(timer);
  }, [selected.mode, selected.assets.length, selected.intervalSeconds]);
  const update = (values: Partial<Occasion>) => setSelected((s) => ({ ...s, ...values }));
  const photoQuality =
    upload?.width && upload?.height ? photoLayout(upload.width, upload.height, focus.x, focus.y) : null;
  async function task(action: () => Promise<void>) {
    setBusy(true);
    setNotice('');
    try {
      await action();
    } catch (e) {
      setNotice((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function save(status = selected.status) {
    if (!selected.title.trim()) throw new Error('Enter an occasion name.');
    const value = await api(
      '/occasions' + (selected.id ? '/' + selected.id : ''),
      selected.id ? 'PUT' : 'POST',
      { ...selected, status },
    );
    setSelected(value);
    await load();
    return value as Occasion;
  }
  const live = state.events.find((e) => e.status === 'scheduled' && e.startsAt <= now && e.endsAt > now);
  const asset = selected.assets[previewIndex % Math.max(1, selected.assets.length)];
  const picture = selected.id && asset ? `/api/occasions/${selected.id}/photos/${asset.id}/image` : null;
  const editable = selected.status !== 'ended' && !selected.purgedAt;
  const conflict = state.events.find(
    (event) =>
      event.id !== selected.id &&
      event.status === 'scheduled' &&
      selected.startsAt < event.endsAt &&
      selected.endsAt > event.startsAt,
  );
  if (signedIn === null)
    return (
      <main className="signin">
        <Icon name="sparkle" size={36} />
        <p>Opening your studio…</p>
      </main>
    );
  if (!signedIn)
    return (
      <main className="signin">
        <div className="signin-card">
          <span className="brandmark">
            <Icon name="sparkle" size={30} />
          </span>
          <p className="eyebrow">A LITTLE SOMETHING FOR HOME</p>
          <h1>Occasion Studio</h1>
          <p>
            Make a familiar screen feel special.
            <br />
            Your TV, a little more personal.
          </p>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              task(async () => {
                await api('/login', 'POST', { password });
                setPassword('');
                await load();
              });
            }}
          >
            <label>
              Owner password
              <input
                autoComplete="current-password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </label>
            <button className="primary" disabled={busy}>
              Open studio <Icon name="arrow" />
            </button>
          </form>
          <p className="subtle">
            <Icon name="lock" size={14} /> Your home, securely connected
          </p>
          {notice && (
            <p role="alert" className="error">
              {notice}
            </p>
          )}
        </div>
      </main>
    );
  return (
    <div className="shell">
      <header className="header">
        <a className="brand" href="#">
          <span className="brandmark">
            <Icon name="sparkle" />
          </span>
          Occasion<span className="brand-light">Studio</span>
        </a>
        <div className="header-right">
          <span className="privacy">
            <Icon name="lock" size={13} /> Your private space
          </span>
          <button
            className="icon-button"
            aria-label="Sign out"
            onClick={() =>
              task(async () => {
                await api('/logout', 'POST', {});
                setSignedIn(false);
              })
            }
          >
            Exit <Icon name="arrow" size={16} />
          </button>
        </div>
      </header>
      <main>
        <section className="intro">
          <div>
            <p className="eyebrow">FROM WHEREVER YOU ARE</p>
            <h1>
              Send a little joy home<span>.</span>
            </h1>
            <p>Some moments deserve more than an ordinary background.</p>
          </div>
          <button
            className="primary"
            onClick={() => {
              setSelected(draft());
              setNotice('');
            }}
          >
            <Icon name="plus" size={18} /> New occasion
          </button>
        </section>
        <div className="workspace">
          <aside className="sidebar">
            <div className="section-heading">
              <h2>Your occasions</h2>
              <span className="count">{state.events.filter((e) => e.status !== 'ended').length}</span>
            </div>
            {state.events.length === 0 && (
              <div className="empty">
                <Icon name="leaf" size={28} />
                <p>
                  A blank page, for
                  <br />
                  your next lovely moment.
                </p>
                <button className="text-button" onClick={() => setSelected(draft())}>
                  Create your first occasion <Icon name="arrow" size={15} />
                </button>
              </div>
            )}
            <div className="occasion-list">
              {[...state.events]
                .sort((a, b) => b.startsAt - a.startsAt)
                .map((e) => (
                  <div className={'occasion-entry ' + (selected.id === e.id ? 'chosen' : '')} key={e.id}>
                    <button
                      className={'occasion-card ' + (selected.id === e.id ? 'chosen' : '')}
                      onClick={() => {
                        setSelected(e);
                        setNotice('');
                      }}
                    >
                      <span
                        className="occasion-symbol"
                        style={{ color: e.color, background: e.color + '15' }}
                      >
                        <Icon name={e.effect === 'petals' ? 'leaf' : 'sparkle'} />
                      </span>
                      <span>
                        <strong>{e.title}</strong>
                        <small>{date(e.startsAt)}</small>
                      </span>
                      <span className={'status ' + (e.id === live?.id ? 'live' : '')}>
                        {e.id === live?.id
                          ? 'Live'
                          : e.status === 'scheduled'
                            ? 'Queued'
                            : e.status === 'ended'
                              ? 'Ended'
                              : 'Draft'}
                      </span>
                    </button>
                    <div className="occasion-actions">
                      <button
                        className="text-button"
                        aria-label={'Edit ' + e.title}
                        disabled={busy || e.status === 'ended'}
                        onClick={() => {
                          setSelected(e);
                          setNotice('');
                          document
                            .querySelector('.editor')
                            ?.scrollIntoView({ block: 'start', behavior: 'smooth' });
                        }}
                      >
                        Edit
                      </button>
                      <button
                        className="text-button delete-occasion"
                        aria-label={'Delete ' + e.title}
                        disabled={busy}
                        onClick={() => {
                          if (!confirm(`Delete "${e.title}" and its photos? This cannot be undone.`)) return;
                          task(async () => {
                            await api(`/occasions/${e.id}`, 'DELETE');
                            setSelected((current) => (current.id === e.id ? draft() : current));
                            await load();
                          });
                        }}
                      >
                        Delete
                      </button>
                    </div>
                  </div>
                ))}
            </div>
            <div className="device-panel">
              <div className="section-heading">
                <h2>
                  <Icon name="tv" size={17} /> Your TV
                </h2>
                <button className="icon-button" onClick={() => setPair(true)} aria-label="Pair a TV">
                  <Icon name="plus" size={17} />
                </button>
              </div>
              {state.devices.filter((d) => !d.revoked).length === 0 ? (
                <>
                  <p>No TV paired yet.</p>
                  <button className="text-button" onClick={() => setPair(true)}>
                    Connect your screen <Icon name="arrow" size={15} />
                  </button>
                </>
              ) : (
                state.devices
                  .filter((d) => !d.revoked)
                  .map((d) => (
                    <div className="device" key={d.id}>
                      <span className={'online-dot ' + (d.seen && now - d.seen < 45000 ? 'online' : '')} />
                      <div className="device-details">
                        <strong>{d.name}</strong>
                        <small>
                          {d.seen ? `Last contact ${date(d.seen)}` : 'Waiting for first connection'}
                          <br />
                          {d.ack ? `${d.ack.state} · version ${d.ack.revision}` : 'No TV acknowledgement yet'}
                        </small>
                      </div>
                      <button
                        className="disconnect-button"
                        onClick={() => {
                          if (confirm('Disconnect this TV from Occasion Studio?'))
                            task(async () => {
                              await api(`/devices/${d.id}/revoke`, 'POST', {});
                              await load();
                            });
                        }}
                      >
                        <Icon name="close" size={13} />
                        Disconnect
                      </button>
                    </div>
                  ))
              )}
            </div>
            <p className="sidebar-note">
              <Icon name="clock" size={16} /> All times are in India
              <br />
              <span>Asia/Kolkata · UTC +05:30</span>
            </p>
          </aside>
          <section className="editor">
            <div className="editor-heading">
              <div>
                <p className="eyebrow">THE LITTLE DETAILS</p>
                <h2>{selected.id ? selected.title : 'Make it a moment'}</h2>
              </div>
            </div>
            <div className="preview-tabs">
              <div role="tablist" aria-label="TV preview">
                <button
                  role="tab"
                  aria-selected={previewTab === 'home'}
                  onClick={() => setPreviewTab('home')}
                >
                  Home screen
                </button>
                <button
                  role="tab"
                  aria-selected={previewTab === 'dream'}
                  onClick={() => setPreviewTab('dream')}
                >
                  Screensaver
                </button>
              </div>
            </div>
            <div className="preview-workbench">
              <div className="tv-preview" style={{ '--accent': selected.color } as React.CSSProperties}>
                {picture && <img className="preview-photo" src={picture} alt="Selected TV wallpaper crop" />}
                <div className="preview-shade" />
                <Motion
                  effect={asset?.effect ?? selected.effect}
                  color={selected.color}
                  dream={previewTab === 'dream'}
                />
                <div className="preview-header">
                  <span>
                    {new Intl.DateTimeFormat('en-IN', {
                      timeZone: 'Asia/Kolkata',
                      hour: 'numeric',
                      minute: '2-digit',
                    }).format(now)}
                  </span>
                  {previewTab === 'home' && <span className="mock-buttons">Inputs　 Wi-Fi　 Settings</span>}
                </div>
                <div className={'preview-message ' + (previewTab === 'dream' ? 'dream-message' : '')}>
                  <span className="preview-kicker">{selected.title}</span>
                  <h3 key={selected.message}>{selected.message || 'Your words, their smile.'}</h3>
                  {previewTab === 'dream' && (
                    <span className="preview-date">
                      {new Intl.DateTimeFormat('en-IN', {
                        timeZone: 'Asia/Kolkata',
                        weekday: 'long',
                        day: 'numeric',
                        month: 'long',
                      }).format(now)}
                    </span>
                  )}
                </div>
                {previewTab === 'home' && (
                  <div className="mock-dock" aria-label="Illustrative app dock">
                    <div>
                      ▶<small>YouTube</small>
                    </div>
                    <div>
                      ✦<small>JioHotstar</small>
                    </div>
                    <div>
                      tv<small>Xiaomi TV+</small>
                    </div>
                    <div>
                      ☁<small>CloudStream</small>
                    </div>
                  </div>
                )}
                <span className="preview-resolution">1280 × 720 · illustrative layout</span>
              </div>
              <fieldset className="preview-tools" disabled={busy || !editable}>
                <p className="eyebrow">EFFECTS</p>
                <div className="effect-control">
                  <label>
                    Finishing touch
                    <select
                      className="effect-list"
                      size={5}
                      value={selected.effect}
                      onChange={(e) => update({ effect: e.target.value })}
                    >
                      {EFFECTS.map((effect) => (
                        <option key={effect.id} value={effect.id}>
                          {effect.label}
                        </option>
                      ))}
                    </select>
                  </label>
                </div>
                {selected.assets.length > 0 && (
                  <div className="preview-photo-picker" aria-label="Choose a photo to style">
                    {selected.assets.map((photo, index) => (
                      <button
                        key={photo.id}
                        className={photo.id === asset?.id ? 'active' : ''}
                        aria-label={'Style photo ' + (index + 1)}
                        aria-pressed={photo.id === asset?.id}
                        onClick={() => setPreviewIndex(index)}
                      >
                        <img src={`/api/occasions/${selected.id}/photos/${photo.id}/thumb`} alt="" />
                        <span>{index + 1}</span>
                      </button>
                    ))}
                  </div>
                )}
                {asset && (
                  <div className="photo-effect-control">
                    <label>
                      Effect for photo {selected.assets.indexOf(asset) + 1}
                      <select
                        className="effect-list"
                        size={5}
                        aria-label="Effect for selected photo"
                        value={asset.effect ?? ''}
                        disabled={busy}
                        onChange={(e) => {
                          const photoId = asset.id,
                            eventId = selected.id,
                            effect = e.target.value || null;
                          task(async () => {
                            const updated = await api(`/occasions/${eventId}/photos/${photoId}`, 'PATCH', {
                              effect,
                            });
                            setSelected((current) =>
                              current.id === eventId
                                ? {
                                    ...current,
                                    assets: current.assets.map((photo) =>
                                      photo.id === photoId ? updated : photo,
                                    ),
                                  }
                                : current,
                            );
                            await load();
                          });
                        }}
                      >
                        <option value="">Use occasion effect</option>
                        {EFFECTS.map((effect) => (
                          <option key={effect.id} value={effect.id}>
                            {effect.label}
                          </option>
                        ))}
                      </select>
                    </label>
                    <p className="subtle">Applies to this photo only. Saved automatically.</p>
                  </div>
                )}
              </fieldset>
            </div>
            {notice && (
              <div className="notice error" role="alert">
                {notice}
                <button className="icon-button" aria-label="Dismiss" onClick={() => setNotice('')}>
                  <Icon name="close" size={16} />
                </button>
              </div>
            )}
            {!editable ? (
              <div className="finished">
                <Icon name="check" />
                <div>
                  <h3>This moment has finished.</h3>
                  <p>Its photos have been removed. Your TV returns to its everyday look.</p>
                </div>
                <button className="secondary" onClick={() => setSelected(draft())}>
                  Make another
                </button>
              </div>
            ) : (
              <fieldset disabled={busy} className="form-content">
                <div className="form-section">
                  <div className="form-title">
                    <span>01</span>
                    <h3>Set the mood</h3>
                  </div>
                  <div className="templates">
                    {Object.entries(state.templates).map(([key, t]) => (
                      <button
                        type="button"
                        key={key}
                        className={selected.template === key ? 'active' : ''}
                        onClick={() =>
                          update({
                            template: key,
                            color: t.color,
                            effect: t.effect,
                            message: t.message,
                            title: t.label,
                          })
                        }
                      >
                        <span style={{ background: t.color }} />
                        {t.label}
                      </button>
                    ))}
                  </div>
                  <div className="two-col">
                    <label>
                      Occasion name
                      <input
                        value={selected.title}
                        maxLength={80}
                        onChange={(e) => update({ title: e.target.value })}
                      />
                    </label>
                    <label>
                      Accent colour
                      <div className="color-control">
                        <input
                          type="color"
                          aria-label="Accent colour"
                          value={selected.color}
                          onChange={(e) => update({ color: e.target.value })}
                        />
                        <span>{selected.color.toUpperCase()}</span>
                      </div>
                    </label>
                  </div>
                  <label className="message-field">
                    <span className="field-label">Words for the screen</span>
                    <textarea
                      value={selected.message}
                      maxLength={140}
                      onChange={(e) => update({ message: e.target.value })}
                      rows={2}
                    />
                  </label>
                </div>
                <div className="form-section">
                  <div className="form-title">
                    <span>02</span>
                    <h3>A picture to come home to</h3>
                  </div>
                  <div className="segmented">
                    <button
                      className={selected.mode === 'fixed' ? 'active' : ''}
                      onClick={() => update({ mode: 'fixed' })}
                    >
                      One wallpaper
                    </button>
                    <button
                      className={selected.mode === 'album' ? 'active' : ''}
                      onClick={() => update({ mode: 'album' })}
                    >
                      Slowly changing album
                    </button>
                  </div>
                  <div className="photo-grid">
                    {selected.assets.map((a, i) => (
                      <div className={'photo-thumb ' + (i === previewIndex ? 'selected' : '')} key={a.id}>
                        <button
                          className="photo-select"
                          onClick={() => setPreviewIndex(i)}
                          aria-label={'Preview photo ' + (i + 1)}
                        >
                          <img
                            src={`/api/occasions/${selected.id}/photos/${a.id}/thumb`}
                            alt={'Photo ' + (i + 1)}
                          />
                        </button>
                        <button
                          className="remove-photo"
                          aria-label={'Remove photo ' + (i + 1)}
                          onClick={() =>
                            task(async () => {
                              await api(`/occasions/${selected.id}/photos/${a.id}`, 'DELETE');
                              const data = await load();
                              setSelected(data.events.find((e) => e.id === selected.id)!);
                            })
                          }
                        >
                          <Icon name="close" size={13} />
                        </button>
                        <span>{i + 1}</span>
                      </div>
                    ))}
                    {selected.assets.length < 6 && (
                      <button className="upload-button" onClick={() => fileInput.current?.click()}>
                        <Icon name="photo" size={24} />
                        <span>Add a photograph</span>
                        <small>JPEG, PNG, WebP · up to 20 MiB</small>
                      </button>
                    )}
                  </div>
                  <input
                    className="visually-hidden"
                    ref={fileInput}
                    type="file"
                    accept="image/jpeg,image/png,image/webp,image/heic,image/avif"
                    onChange={(e) => {
                      const file = e.target.files?.[0];
                      if (file) {
                        setNotice('');
                        setUpload({
                          file,
                          url: URL.createObjectURL(file),
                          needsName: !selected.title.trim(),
                        });
                        setFocus({ x: 0.5, y: 0.5 });
                      }
                      e.target.value = '';
                    }}
                  />
                  {selected.mode === 'album' && (
                    <label className="interval">
                      Change photo every
                      <select
                        value={selected.intervalSeconds}
                        onChange={(e) => update({ intervalSeconds: Number(e.target.value) })}
                      >
                        {[30, 60, 90, 120, 300, 600].map((n) => (
                          <option key={n} value={n}>
                            {n < 60 ? '30 seconds' : `${n / 60} minutes`}
                          </option>
                        ))}
                      </select>
                    </label>
                  )}
                  <p className="helper">
                    {selected.mode === 'fixed'
                      ? 'The first photo is your fixed wallpaper. '
                      : 'Up to six photos, gently crossfaded. '}
                    Leave empty for a colour backdrop. Photos are prepared at your TV’s display size.
                  </p>
                </div>
                <div className="form-section">
                  <div className="form-title">
                    <span>03</span>
                    <h3>Choose its moment</h3>
                    <span className="timezone">India time · IST</span>
                  </div>
                  <div className="two-col">
                    <label>
                      Starts
                      <input
                        type="datetime-local"
                        value={inputDate(selected.startsAt)}
                        onChange={(e) => {
                          if (e.target.value) update({ startsAt: fromInput(e.target.value) });
                        }}
                      />
                    </label>
                    <label>
                      Ends
                      <input
                        type="datetime-local"
                        value={inputDate(selected.endsAt)}
                        onChange={(e) => {
                          if (e.target.value) update({ endsAt: fromInput(e.target.value) });
                        }}
                      />
                    </label>
                  </div>
                  <div className="duration">
                    <span>Make it last</span>
                    {[6, 12].map((h) => (
                      <button
                        className={selected.endsAt - selected.startsAt === h * 3600000 ? 'active' : ''}
                        key={h}
                        onClick={() => update({ endsAt: selected.startsAt + h * 3600000 })}
                      >
                        {h} hours
                      </button>
                    ))}
                  </div>
                  {conflict && (
                    <p className="schedule-conflict" role="status">
                      This time overlaps “{conflict.title}” ({date(conflict.startsAt)} –{' '}
                      {date(conflict.endsAt)}). Choose another time before scheduling. You can still save a
                      draft.
                    </p>
                  )}
                  <div className="retention">
                    <Icon name="leaf" size={19} />
                    <p>
                      Here for the occasion, gone afterwards.
                      <span>
                        All event photos and generated copies are deleted after it ends. Your normal Home and
                        screensaver return automatically.
                      </span>
                    </p>
                  </div>
                </div>
                <div className="editor-footer">
                  <button
                    className="secondary"
                    onClick={() =>
                      task(async () => {
                        await save('draft');
                      })
                    }
                  >
                    Save draft
                  </button>
                  <div>
                    {selected.id && (
                      <button
                        className="text-button"
                        onClick={() => {
                          if (confirm('End this occasion and delete all its photos now?'))
                            task(async () => {
                              await api(`/occasions/${selected.id}/end`, 'POST', {});
                              const data = await load();
                              setSelected(data.events.find((e) => e.id === selected.id)!);
                            });
                        }}
                      >
                        End now
                      </button>
                    )}
                    <button
                      className="secondary"
                      onClick={() =>
                        task(async () => {
                          const updated = await api(
                            selected.id ? `/occasions/${selected.id}/start` : '/occasions/start',
                            'POST',
                            selected,
                          );
                          setSelected(updated);
                          await load();
                        })
                      }
                    >
                      Start now
                    </button>
                    <button
                      className="primary"
                      onClick={() =>
                        task(async () => {
                          await save('scheduled');
                        })
                      }
                    >
                      {busy ? 'Saving…' : 'Schedule occasion'}
                      <Icon name="arrow" size={17} />
                    </button>
                  </div>
                </div>
              </fieldset>
            )}
          </section>
        </div>
        <footer className="page-footer">
          Made for your home. Controlled from anywhere.<span>Photos expire. The memories don’t.</span>
        </footer>
      </main>
      {pair && (
        <div className="modal-shade">
          <section className="modal" role="dialog" aria-modal="true" aria-labelledby="pair-title">
            <button className="modal-close icon-button" onClick={() => setPair(false)} aria-label="Close">
              <Icon name="close" />
            </button>
            <span className="brandmark">
              <Icon name="tv" size={25} />
            </span>
            <h2 id="pair-title">Bring your TV into the studio.</h2>
            <p>
              On the TV, open Occasion Studio settings, enter your server’s HTTPS address and choose Pair.
              Enter the six-digit code here.
            </p>
            <form
              onSubmit={(e) => {
                e.preventDefault();
                task(async () => {
                  await api('/pair/approve', 'POST', { code, name: 'Living room TV' });
                  setPair(false);
                  setCode('');
                  await load();
                });
              }}
            >
              <label>
                Code on your TV
                <input
                  inputMode="numeric"
                  pattern="[0-9]{6}"
                  maxLength={6}
                  value={code}
                  onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
                  autoFocus
                  required
                />
              </label>
              <button className="primary" disabled={busy}>
                Connect TV <Icon name="arrow" />
              </button>
            </form>
            <p className="helper">Codes expire after five minutes. No owner password is stored on the TV.</p>
            {notice && (
              <p role="alert" className="error">
                {notice}
              </p>
            )}
          </section>
        </div>
      )}
      {upload && (
        <div className="modal-shade">
          <section className="modal crop-modal" role="dialog" aria-modal="true" aria-labelledby="crop-title">
            <h2 id="crop-title">Give it a little breathing room.</h2>
            <p>Move the photo’s focal point. Keep faces clear of the clock and app dock.</p>
            {upload.needsName && (
              <label className="crop-name">
                Occasion name
                <input
                  value={selected.title}
                  maxLength={80}
                  placeholder="For example, Birthday at home"
                  onChange={(e) => {
                    update({ title: e.target.value });
                    setNotice('');
                  }}
                />
                <span className="helper">
                  Give this occasion a name so we can attach your photograph. Your selected image stays here.
                </span>
              </label>
            )}
            <div className="crop-preview">
              <img
                src={upload.url}
                style={{ objectPosition: `${focus.x * 100}% ${focus.y * 100}%` }}
                alt="Wallpaper crop preview"
                onLoad={(e) => {
                  const { naturalWidth: width, naturalHeight: height } = e.currentTarget;
                  setUpload((current) =>
                    current && current.url === upload.url ? { ...current, width, height } : current,
                  );
                }}
                onError={() =>
                  setUpload((current) =>
                    current && current.url === upload.url ? { ...current, previewError: true } : current,
                  )
                }
              />
              <span className="safe-top">Clock & controls</span>
              <span className="safe-bottom">App dock</span>
            </div>
            {photoQuality && (
              <div
                className={'photo-quality ' + (photoQuality.upscaled ? 'quality-warning' : '')}
                role="status"
              >
                <strong>
                  {photoQuality.upscaled
                    ? 'Small photo · enlargement needed'
                    : 'Automatically fitted to your TV'}
                </strong>
                <span>
                  {photoQuality.sourceWidth} × {photoQuality.sourceHeight} original · {photoQuality.cropWidth}{' '}
                  × {photoQuality.cropHeight} crop → 1280 × 720
                </span>
                <p>
                  {photoQuality.warning ||
                    'We crop to 16:9 automatically and resize without stretching. Adjust the focal point only if you want to.'}
                </p>
              </div>
            )}
            {upload.previewError && (
              <p className="helper" role="status">
                This browser cannot preview the photo. The server will try to prepare it and report its size
                and quality.
              </p>
            )}
            <div className="two-col">
              <label>
                Horizontal
                <input
                  type="range"
                  min="0"
                  max="1"
                  step=".01"
                  value={focus.x}
                  onChange={(e) => setFocus((s) => ({ ...s, x: +e.target.value }))}
                />
              </label>
              <label>
                Vertical
                <input
                  type="range"
                  min="0"
                  max="1"
                  step=".01"
                  value={focus.y}
                  onChange={(e) => setFocus((s) => ({ ...s, y: +e.target.value }))}
                />
              </label>
            </div>
            <div className="modal-actions">
              <button className="secondary" disabled={busy} onClick={() => setUpload(null)}>
                Cancel
              </button>
              <button
                className="primary"
                disabled={busy}
                onClick={() =>
                  task(async () => {
                    const event = selected.id ? await save() : await save('draft');
                    await api(
                      `/occasions/${event.id}/photos?x=${focus.x}&y=${focus.y}`,
                      'POST',
                      upload.file,
                      true,
                    );
                    const data = await load();
                    setSelected(data.events.find((e) => e.id === event.id)!);
                    setUpload(null);
                    setNotice('');
                  })
                }
              >
                {busy ? 'Preparing photograph…' : 'Use this photograph'}
                <Icon name="check" size={17} />
              </button>
            </div>
            {notice && (
              <p role="alert" className="error">
                {notice}
              </p>
            )}
          </section>
        </div>
      )}
    </div>
  );
}
createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
