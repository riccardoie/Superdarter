import { useEffect, useId, useRef, useState } from 'react';

export default function FriendAutocomplete({
  value,
  suggestions: matches,
  enabled = true,
  label,
  placeholder,
  onChange,
  onAdd,
}) {
  const suggestions = matches.slice(0, 3);
  const id = useId();
  const root = useRef(null);
  const input = useRef(null);
  const activeOption = useRef(null);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState('');
  const expanded = enabled && open && suggestions.length > 0;
  const activeIndex = suggestions.indexOf(active);

  useEffect(() => {
    const closeOutside = (event) => {
      if (!root.current.contains(event.target)) setOpen(false);
    };
    document.addEventListener('pointerdown', closeOutside);
    return () => document.removeEventListener('pointerdown', closeOutside);
  }, []);

  useEffect(() => {
    if (expanded) activeOption.current?.scrollIntoView({ block: 'nearest' });
  }, [active, expanded]);

  function choose(name) {
    onChange(name);
    input.current.focus();
    setOpen(false);
    setActive('');
  }

  function handleKeyDown(event) {
    if (
      enabled &&
      suggestions.length &&
      ['ArrowDown', 'ArrowUp'].includes(event.key)
    ) {
      event.preventDefault();
      const direction = event.key === 'ArrowDown' ? 1 : -1;
      const index =
        !expanded || activeIndex < 0
          ? direction === 1
            ? 0
            : suggestions.length - 1
          : (activeIndex + direction + suggestions.length) % suggestions.length;
      setOpen(true);
      setActive(suggestions[index]);
    } else if (event.key === 'Enter') {
      event.preventDefault();
      if (expanded && activeIndex >= 0) choose(active);
      else onAdd();
    } else if (event.key === 'Escape' && expanded) {
      event.preventDefault();
      event.stopPropagation();
      setOpen(false);
      setActive('');
    } else if (event.key === 'Tab') {
      setOpen(false);
    }
  }

  return (
    <div
      className="friend-autocomplete"
      ref={root}
      onBlur={(event) => {
        if (
          event.relatedTarget &&
          !event.currentTarget.contains(event.relatedTarget)
        )
          setOpen(false);
      }}
    >
      <input
        ref={input}
        className="player-input"
        type="text"
        maxLength={255}
        autoComplete="off"
        autoCapitalize="none"
        spellCheck={false}
        aria-label={label}
        placeholder={placeholder}
        role={enabled ? 'combobox' : undefined}
        aria-autocomplete={enabled ? 'list' : undefined}
        aria-expanded={enabled ? expanded : undefined}
        aria-controls={expanded ? `${id}-suggestions` : undefined}
        aria-activedescendant={
          expanded && activeIndex >= 0
            ? `${id}-option-${activeIndex}`
            : undefined
        }
        value={value}
        onFocus={() => {
          setOpen(true);
          setActive('');
        }}
        onChange={(event) => {
          onChange(event.target.value);
          setActive('');
          setOpen(true);
        }}
        onKeyDown={handleKeyDown}
      />
      {expanded && (
        <ul
          className="friend-suggestions"
          id={`${id}-suggestions`}
          role="listbox"
          aria-label="Matching friends"
        >
          {suggestions.map((name, index) => (
            <li
              key={name}
              id={`${id}-option-${index}`}
              role="option"
              aria-selected={active === name}
              ref={active === name ? activeOption : undefined}
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => choose(name)}
            >
              <span className="friend-suggestion-avatar" aria-hidden="true">
                {name[0].toUpperCase()}
              </span>
              <span>{name}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
