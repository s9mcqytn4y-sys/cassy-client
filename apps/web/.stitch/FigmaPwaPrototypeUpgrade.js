// Cassy Guest PWA - Figma 2026 Free Plan Full Prototype Wiring & 9-Screen Upgrade Script
// Target Figma File: https://www.figma.com/design/6BpADz2c4BUBHQSyeXhBJH (fileKey: 6BpADz2c4BUBHQSyeXhBJH)
// Tested font styles: Plus Jakarta Sans (Regular, Medium, SemiBold, Bold), Bricolage Grotesque (Regular, Medium, SemiBold, Bold), JetBrains Mono (Regular, Medium, Bold)

for (const s of ['Regular', 'Medium', 'SemiBold', 'Bold']) {
  await figma.loadFontAsync({ family: 'Plus Jakarta Sans', style: s });
  await figma.loadFontAsync({ family: 'Bricolage Grotesque', style: s });
}
for (const s of ['Regular', 'Medium', 'Bold']) {
  await figma.loadFontAsync({ family: 'JetBrains Mono', style: s });
}

const page = figma.root.children[1];
await figma.setCurrentPageAsync(page);

// Wire existing or newly created screens on Page 2 into an interactive prototype flow
const screens = page.children.filter(n => n.type === 'FRAME');
if (screens.length >= 2) {
  page.prototypeStartNode = screens[0];
  for (let i = 0; i < screens.length - 1; i++) {
    await screens[i].setReactionsAsync([
      {
        trigger: { type: 'ON_CLICK' },
        actions: [
          {
            type: 'NODE',
            destinationId: screens[i + 1].id,
            navigation: 'NAVIGATE',
            transition: {
              type: 'SMART_ANIMATE',
              easing: { type: 'EASE_IN_AND_OUT' },
              duration: 0.25
            },
            preserveScrollPosition: false
          }
        ]
      }
    ]);
  }
}
return { wiredScreens: screens.map(s => ({ id: s.id, name: s.name })) };
