"use client";

import { useState } from "react";
import { Dialog } from "./Dialog";
import { MAX_NAME, MAX_VIEWS, type SavedView } from "@/lib/savedviews";

type Settings = Omit<SavedView, "id" | "name">;

const newId = () => Math.random().toString(36).slice(2, 12) + Date.now().toString(36);

/**
 * The Collection page's Views ▾ menu: the saved views (one click opens one), "Save current view…" and "Rename or
 * delete…". [onSave] stores the whole list and answers false when that didn't work.
 */
export function ViewsMenu({
  views,
  current,
  onApply,
  onSave,
}: {
  views: SavedView[];
  current: Settings;
  onApply: (v: SavedView) => void;
  onSave: (list: SavedView[]) => Promise<boolean>;
}) {
  const [saving, setSaving] = useState(false);
  const [managing, setManaging] = useState(false);
  return (
    <>
      <details className="menu">
        <summary className="btn">Views ▾</summary>
        <div className="menu-items from-left" onClick={(e) => ((e.currentTarget.parentElement as HTMLDetailsElement).open = false)}>
          {views.map((v) => (
            <button key={v.id} onClick={() => onApply(v)}>
              {v.name}
            </button>
          ))}
          {views.length === 0 && <span className="muted small menu-note">No saved views yet</span>}
          <button onClick={() => setSaving(true)} disabled={views.length >= MAX_VIEWS}>
            Save current view…
          </button>
          <button onClick={() => setManaging(true)} disabled={views.length === 0}>
            Rename or delete…
          </button>
        </div>
      </details>
      {saving && (
        <SaveViewDialog
          names={views.map((v) => v.name)}
          onClose={() => setSaving(false)}
          onSave={async (name) => {
            const same = views.find((v) => v.name.toLowerCase() === name.toLowerCase());
            const list = same ? views.map((v) => (v.id === same.id ? { ...current, id: v.id, name } : v)) : [...views, { ...current, id: newId(), name }];
            if (await onSave(list)) setSaving(false);
          }}
        />
      )}
      {managing && (
        <ManageViewsDialog
          views={views}
          onClose={() => setManaging(false)}
          onSave={async (list) => {
            if (await onSave(list)) setManaging(false);
          }}
        />
      )}
    </>
  );
}

function SaveViewDialog({ names, onSave, onClose }: { names: string[]; onSave: (name: string) => void; onClose: () => void }) {
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const n = name.trim();
  const replaces = names.some((x) => x.toLowerCase() === n.toLowerCase());
  return (
    <Dialog title="Save current view" onClose={onClose}>
      <form
        className="stack"
        onSubmit={async (e) => {
          e.preventDefault();
          if (!n) return;
          setBusy(true);
          await onSave(n);
          setBusy(false);
        }}
      >
        <p className="small muted">Saves the binder, search, filter, sort, columns, Group printings and list or picture view, with your account.</p>
        <label className="field">
          <span>Name</span>
          <input autoFocus maxLength={MAX_NAME} value={name} placeholder="For example: Trade fodder" onChange={(e) => setName(e.target.value)} />
        </label>
        {replaces && <p className="small">A view with this name is there already; saving replaces it.</p>}
        <div className="dialog-actions">
          <span className="spacer" />
          <button type="button" className="btn" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn primary" disabled={busy || !n}>
            {replaces ? "Replace" : "Save"}
          </button>
        </div>
      </form>
    </Dialog>
  );
}

function ManageViewsDialog({ views, onSave, onClose }: { views: SavedView[]; onSave: (list: SavedView[]) => void; onClose: () => void }) {
  const [list, setList] = useState(views);
  const [busy, setBusy] = useState(false);
  const ok = list.every((v) => v.name.trim()) && new Set(list.map((v) => v.name.trim().toLowerCase())).size === list.length;
  return (
    <Dialog title="Rename or delete views" onClose={onClose}>
      <div className="stack">
        {list.length === 0 && <p className="muted">No views left.</p>}
        {list.map((v, i) => (
          <div key={v.id} className="inline">
            <input
              aria-label={`Name of view ${i + 1}`}
              maxLength={MAX_NAME}
              value={v.name}
              onChange={(e) => setList(list.map((x) => (x.id === v.id ? { ...x, name: e.target.value } : x)))}
            />
            <button className="btn danger" onClick={() => setList(list.filter((x) => x.id !== v.id))}>
              Delete
            </button>
          </div>
        ))}
        {!ok && <p className="small">Every view needs a name of its own.</p>}
        <div className="dialog-actions">
          <span className="spacer" />
          <button className="btn" onClick={onClose}>
            Cancel
          </button>
          <button
            className="btn primary"
            disabled={busy || !ok}
            onClick={async () => {
              setBusy(true);
              await onSave(list.map((v) => ({ ...v, name: v.name.trim() })));
              setBusy(false);
            }}
          >
            Save
          </button>
        </div>
      </div>
    </Dialog>
  );
}
