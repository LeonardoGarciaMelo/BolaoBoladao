type ReconcileOptions<T> = {
  key: (item: T) => string;
  create: (item: T) => HTMLElement;
  update: (element: HTMLElement, item: T) => void;
};

export const reconcileMatchesById = <T>(container: HTMLElement, items: T[], options: ReconcileOptions<T>) => {
  const activeElement = document.activeElement instanceof HTMLElement && container.contains(document.activeElement)
    ? document.activeElement
    : undefined;
  const existing = new Map<string, HTMLElement>();
  Array.from(container.children).forEach((child) => {
    if (!(child instanceof HTMLElement)) return;
    if (child.dataset.matchId) existing.set(child.dataset.matchId, child);
    else child.remove();
  });

  items.forEach((item, index) => {
    const key = options.key(item);
    const element = existing.get(key) ?? options.create(item);
    element.dataset.matchId = key;
    options.update(element, item);
    existing.delete(key);

    const expectedPosition = container.children.item(index);
    if (expectedPosition !== element) container.insertBefore(element, expectedPosition);
  });

  existing.forEach((element) => element.remove());
  if (activeElement?.isConnected && document.activeElement !== activeElement) activeElement.focus({ preventScroll: true });
};

export const updateText = (element: Element | null, value: string) => {
  if (element && element.textContent !== value) element.textContent = value;
};

export const highlightScore = (element: HTMLElement) => {
  element.classList.remove("match-score--updated");
  void element.offsetWidth;
  element.classList.add("match-score--updated");
};

export const updateScore = (host: HTMLElement, element: HTMLElement, value: string, copy: string) => {
  updateText(element, copy);
  if (host.dataset.score && host.dataset.score !== value) highlightScore(element);
  if (host.dataset.score !== value) host.dataset.score = value;
};
