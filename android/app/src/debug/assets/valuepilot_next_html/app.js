'use strict';
/* Visual proof of concept only. No real product images, backend, Firebase, App Check,
   native storage, camera access, location or production shopping actions. */
const DEMO_PRODUCTS = Object.freeze([
  {id:'milk-armonia-1l', brand:'Armonía', name:'Leche Entera Clásica', variant:'UAT larga vida · 3% tenor graso · sachet', quantity:'1.000 ml', unit:'L', liters:1, price:1190, store:'Dia Online · Recoleta', freshness:'Muestra de diseño', label:'Ejemplo de precio bajo'},
  {id:'milk-serenisima-1l', brand:'La Serenísima', name:'Leche Entera Clásica 3%', variant:'Pasteurizada · sachet refrigerado · 3% tenor graso', quantity:'1.000 ml', unit:'L', liters:1, price:1290, store:'Coto · Almagro', freshness:'Muestra de diseño', label:'Sachet 1 L'},
  {id:'milk-light-1l', brand:'La Serenísima', name:'Leche Descremada 1%', variant:'Parcialmente descremada · calcio activo · sachet', quantity:'1.000 ml', unit:'L', liters:1, price:1290, store:'Coto · Abasto', freshness:'Muestra de diseño', label:'Variedad liviana'},
  {id:'milk-ilolay-1l', brand:'Ilolay', name:'Leche Entera Clásica', variant:'Homogeneizada · larga vida · envase Tetra Brik', quantity:'1.000 ml', unit:'L', liters:1, price:1340, store:'Carrefour Express · Palermo', freshness:'Muestra de diseño', label:'Tetra Brik'},
  {id:'rice-arroz-1kg', brand:'Molinos Ala', name:'Arroz Largo Fino 00000', variant:'Bolsa 1 kg · variedad largo fino', quantity:'1 kg', unit:'kg', liters:1, price:1890, store:'Coto · Almagro', freshness:'Muestra de diseño', label:'Almacén básico'},
  {id:'oil-oliva-500ml', brand:'Aceite de Oliva', name:'Extra Virgen', variant:'Botella 500 ml · aceite de oliva', quantity:'500 ml', unit:'L', liters:.5, price:7450, store:'Jumbo · Palermo', freshness:'Muestra de diseño', label:'Aceites y grasas'}
]);
const LANGUAGES=Object.freeze({EN:'en',ES_AR:'es-AR'});
const LANGUAGE_KEY='valuepilot-next-language';
/* Stable copy keys keep the language switch separate from layout and product state. */
const COPY=Object.freeze({
  demoTitle:{es:'PROTOTIPO INTERACTIVO',en:'INTERACTIVE PROTOTYPE'},
  demoMessage:{es:'Precios y productos ficticios. No son ofertas reales.',en:'Sample prices and products. Not real offers.'},
  backHome:{es:'Volver al inicio',en:'Back to home'},
  back:{es:'Volver',en:'Back'},
  themeAria:{es:'Cambiar modo claro/oscuro',en:'Switch light or dark mode'},
  themeTitle:{es:'Tema',en:'Theme'},
  preferences:{es:'Preferencias',en:'Preferences'},
  navLabel:{es:'Navegación principal',en:'Primary navigation'},
  navHome:{es:'Inicio',en:'Home'},
  navSearch:{es:'Buscar',en:'Search'},
  navBasket:{es:'Canasta',en:'Basket'},
  navSaved:{es:'Guardados',en:'Saved'},
  unitPrices:{es:'PRECIOS UNITARIOS NORMALIZADOS',en:'NORMALIZED UNIT PRICES'},
  heroTitle:{es:'Decidí mejor en cada compra.',en:'Make a better choice every time.'},
  heroCopy:{es:'Compará el valor por kilo o litro y planificá el recorrido, sin perder claridad.',en:'Compare value by kilo or litre and plan your trip without losing clarity.'},
  searchProduct:{es:'Buscar un producto',en:'Search for a product'},
  searchPlaceholder:{es:'Buscar leche, arroz, aceite...',en:'Search milk, rice, oil...'},
  search:{es:'Buscar',en:'Search'},
  demoBasket:{es:'CANASTA DE PRUEBA',en:'SAMPLE BASKET'},
  items:{es:'ítems',en:'items'},
  basketTitle:{es:'Tu canasta',en:'Your basket'},
  samplePrices:{es:'Precios ilustrativos · no verificables',en:'Sample prices · not verifiable'},
  basketEmptyCopy:{es:'Agregá productos para comparar',en:'Add products to compare'},
  viewBasket:{es:'Ver canasta',en:'View basket'},
  tripPlanner:{es:'PLANIFICADOR DE RECORRIDOS',en:'TRIP PLANNER'},
  nextTrip:{es:'Tu próximo viaje',en:'Your next trip'},
  tripCopy:{es:'Calculá combustible, peajes y estacionamiento.',en:'Calculate fuel, tolls and parking.'},
  planTrip:{es:'Planificar viaje',en:'Plan trip'},
  comparison:{es:'Rubros de comparación',en:'Comparison categories'},
  viewAll:{es:'Ver todos',en:'View all'},
  dairy:{es:'Lácteos',en:'Dairy'},
  pantry:{es:'Almacén',en:'Pantry'},
  oils:{es:'Aceites',en:'Oils'},
  sampleUnitPrices:{es:'Precios unitarios de ejemplo',en:'Sample unit prices'},
  demoOnly:{es:'Solo demostración',en:'Demo only'},
  designNoticeTitle:{es:'VISTA DE DISEÑO · Datos inventados.',en:'DESIGN VIEW · Sample data.'},
  designNotice:{es:'Usamos ejemplos únicamente para comprobar el layout. Este prototipo no consulta precios, inventario ni comercios reales.',en:'Examples are used only to check layout. This prototype does not query real prices, inventory or stores.'},
  smartSearch:{es:'BÚSQUEDA INTELIGENTE',en:'SMART SEARCH'},
  bestValue:{es:'Encontrá el mejor valor',en:'Find the best value'},
  noPhotos:{es:'Buenos Aires (CABA) · Sin fotos nativas',en:'Buenos Aires (CABA) · No native photos'},
  nameBrandQuantity:{es:'Nombre, marca o cantidad',en:'Name, brand or quantity'},
  clearSearch:{es:'Borrar búsqueda',en:'Clear search'},
  lowest:{es:'Menor $/unidad',en:'Lowest unit price'},
  brandSort:{es:'Marca A–Z',en:'Brand A–Z'},
  highest:{es:'Mayor $/unidad',en:'Highest unit price'},
  all:{es:'Todos',en:'All'},
  milk:{es:'Leche',en:'Milk'},
  rice:{es:'Arroz',en:'Rice'},
  oil:{es:'Aceite',en:'Oil'},
  result:{es:'resultado',en:'result'},
  results:{es:'resultados',en:'results'},
  comparable:{es:'Por unidad comparable',en:'Comparable unit price'},
  lowestExample:{es:'↘ Ejemplo con menor $/unidad',en:'↘ Lowest sample unit price'},
  noMatches:{es:'No hay ejemplos coincidentes',en:'No matching samples'},
  tryExamples:{es:'Probá «leche», «arroz» o «aceite». Esta pantalla funciona sin conexión a fuentes de precios.',en:'Try “milk”, “rice” or “oil”. This screen works without price sources.'},
  showExamples:{es:'Ver ejemplos',en:'Show examples'},
  unitPriceSample:{es:'PRECIO UNITARIO · MUESTRA',en:'UNIT PRICE · SAMPLE'},
  packageTotal:{es:'Total paquete',en:'Package total'},
  fictitiousPrice:{es:'Precio ficticio',en:'Sample price'},
  fictitious:{es:'Ficticio',en:'Sample'},
  googleImages:{es:'Ver en Google Images',en:'View in Google Images'},
  addPrice:{es:'Sumar',en:'Add'},
  save:{es:'Agregar a',en:'Save to'},
  saved:{es:'guardados',en:'Saved'},
  removeSaved:{es:'Quitar de guardados',en:'Remove from Saved'},
  addAria:{es:'Agregar',en:'Add'},
  backResults:{es:'Volver a resultados',en:'Back to results'},
  exampleCaba:{es:'EJEMPLO CABA',en:'CABA SAMPLE'},
  noCatalogPhotos:{es:'Sin fotos en el catálogo',en:'No catalog photos'},
  photoCopy:{es:'Podés examinar el envase en Google Images sin salir de tu recorrido de compra.',en:'Inspect the package in Google Images without leaving your shopping journey.'},
  googleAction:{es:'Ver fotos en Google Images',en:'View package in Google Images'},
  externalDisclaimer:{es:'Las imágenes externas pueden no coincidir con el producto, el envase o la cantidad exacta.',en:'External images may not match the exact product, package or quantity.'},
  priceLiter:{es:'LITRO',en:'LITRE'},
  priceKg:{es:'KG',en:'KG'},
  sampleUnverified:{es:'Dato ficticio · no verificado',en:'Sample · not verified'},
  context:{es:'Contexto de comparación',en:'Comparison context'},
  illustrative:{es:'Ilustrativo',en:'Illustrative'},
  evidenceCopy:{es:'La aplicación real mostrará precios, disponibilidad y tiendas únicamente cuando existan datos respaldados por las fuentes de ValuePilot.',en:'The full app shows prices, availability and stores only when ValuePilot sources provide supporting evidence.'},
  noEvidence:{es:'No hay evidencia de precio real en este prototipo.',en:'There is no real price evidence in this prototype.'},
  basketDemo:{es:'Canasta de demostración',en:'Sample basket'},
  quantityPackages:{es:'Cantidad de paquetes',en:'Package quantity'},
  reduce:{es:'Reducir cantidad',en:'Decrease quantity'},
  increase:{es:'Aumentar cantidad',en:'Increase quantity'},
  addDetail:{es:'Agregar',en:'Add'},
  privateBasket:{es:'CANASTA PRIVADA · DEMOSTRACIÓN',en:'PRIVATE BASKET · DEMO'},
  basketSubtitle:{es:'Sumá productos y compará cantidades.',en:'Add products and compare quantities.'},
  basketProducts:{es:'PRODUCTOS EN TU CANASTA',en:'PRODUCTS IN YOUR BASKET'},
  samplePrice:{es:'Precio de prueba',en:'Sample price'},
  removeOne:{es:'Quitar uno',en:'Remove one'},
  addOne:{es:'Agregar uno',en:'Add one'},
  exampleTotal:{es:'Total de ejemplo',en:'Sample total'},
  notVerified:{es:'NO VERIFICADO',en:'NOT VERIFIED'},
  notOffer:{es:'No representa una oferta, un ahorro ni una compra.',en:'Does not represent an offer, savings or a purchase.'},
  estimateTravel:{es:'Estimar traslado',en:'Estimate travel'},
  keepSearching:{es:'Seguir buscando',en:'Keep searching'},
  clearDemo:{es:'Vaciar demostración',en:'Clear sample'},
  emptyBasket:{es:'Tu canasta está vacía',en:'Your basket is empty'},
  chooseExamples:{es:'Elegí algunos productos de ejemplo para probar la experiencia. Los precios son ilustrativos.',en:'Choose a few sample products to try the experience. Prices are illustrative.'},
  exploreProducts:{es:'Explorar productos',en:'Explore products'},
  basketNotice:{es:'Esta canasta funciona únicamente dentro del prototipo HTML. No modifica tu ValuePilot real, su base de datos ni las compras del teléfono.',en:'This basket only exists inside the HTML prototype. It does not change your real ValuePilot, database or purchases on the phone.'},
  personalList:{es:'TU LISTA PERSONAL · DEMO',en:'YOUR PERSONAL LIST · DEMO'},
  savedTitle:{es:'Guardados',en:'Saved'},
  favorites:{es:'Tus productos favoritos, a mano.',en:'Your favourite products, close at hand.'},
  fictitiousInfo:{es:'Información ficticia',en:'Fictitious information'},
  openDetails:{es:'Abrir detalles',en:'Open details'},
  savePrompt:{es:'Guardá lo que quieras recordar',en:'Save something to remember'},
  tapBookmark:{es:'Tocá el marcador en los resultados. No se guardan productos reales.',en:'Tap the bookmark in results. Real products are not stored here.'},
  searchProducts:{es:'Buscar productos',en:'Search products'},
  otherTools:{es:'Otras herramientas',en:'Other tools'},
  tripEstimator:{es:'Estimador de recorrido',en:'Trip estimator'},
  existingFlows:{es:'La versión completa conserva los flujos de observaciones, Watch y precios en la capa Kotlin existente.',en:'The full version keeps observations, Watch and price history in the existing Kotlin layer.'},
  tripDemo:{es:'PLANIFICADOR · DEMOSTRACIÓN',en:'TRIP PLANNER · DEMO'},
  tripTitle:{es:'¿Cuánto cuesta llegar?',en:'What will it cost to get there?'},
  tripDescription:{es:'Calculá tu traslado con los datos que ingreses. No sugerimos rutas ni distancias inventadas.',en:'Calculate your trip with the values you enter. We do not invent routes or distances.'},
  distance:{es:'Distancia total del viaje (km)',en:'Total trip distance (km)'},
  consumption:{es:'Consumo (litros / 100 km)',en:'Fuel consumption (litres / 100 km)'},
  fuelPrice:{es:'Precio del combustible (ARS / litro)',en:'Fuel price (ARS / litre)'},
  tolls:{es:'Peajes + estacionamiento (ARS)',en:'Tolls + parking (ARS)'},
  calculate:{es:'Calcular costo',en:'Calculate cost'},
  tripNotice:{es:'El resultado usa únicamente tus entradas y no se registra ni compara con fuentes externas.',en:'The result uses only your entries and is not saved or compared with external sources.'},
  estimated:{es:'Costo estimado con tus datos',en:'Estimated cost from your values'},
  additional:{es:'adicionales',en:'additional'},
  previewPreferences:{es:'PREFERENCIAS DE PREVISUALIZACIÓN',en:'PREVIEW PREFERENCES'},
  personalize:{es:'Personalizá tu vista',en:'Customize your view'},
  visualTheme:{es:'Tema visual',en:'Visual theme'},
  light:{es:'Claro',en:'Light'},
  dark:{es:'Oscuro',en:'Dark'},
  language:{es:'Idioma',en:'Language'},
  spanishArgentina:{es:'Español (Argentina)',en:'Argentine Spanish'},
  english:{es:'Inglés',en:'English'},
  market:{es:'Mercado',en:'Market'},
  settingsNotice:{es:'Estas opciones solo afectan la maqueta HTML. No se guardan preferencias del ValuePilot instalado.',en:'These options affect only the HTML mock-up. They do not change preferences in the installed ValuePilot.'},
  toastSaved:{es:'Agregado a Guardados · solo demo',en:'Added to Saved · demo only'},
  toastRemoved:{es:'Quitado de Guardados',en:'Removed from Saved'},
  toastBasket:{es:'Producto agregado a la canasta de demostración',en:'Product added to the sample basket'},
  toastNativeGoogle:{es:'Abriendo Google Images en Android',en:'Opening Google Images in Android'},
  toastNativeError:{es:'No se pudo abrir la integración nativa',en:'The native integration could not open'},
  toastExternalGoogle:{es:'Google Images se abre fuera del prototipo. Las fotos son referencias externas.',en:'Google Images opens outside the prototype. Photos are external references.'},
  toastCleared:{es:'Canasta de ejemplo vaciada',en:'Sample basket cleared'},
  invalidInputs:{es:'Revisá los valores ingresados',en:'Check the values you entered'},
  demoNumbers:{es:'Las cifras de esta vista son ficticias y sirven exclusivamente para evaluar la apariencia y los controles. No están conectadas a Firebase ni a precios reales.',en:'The figures on this screen are fictional and only evaluate appearance and controls. They are not connected to Firebase or live prices.'},
  searchInput:{es:'Buscar producto',en:'Search product'},
  sampleSuffix:{es:'de muestra',en:'sample'},
  pesos:{es:'pesos',en:'ARS'},
  packageTotalUpper:{es:'TOTAL PAQUETE',en:'PACKAGE TOTAL'},
  pricePrefix:{es:'PRECIO / ',en:'PRICE / '},
  sampleWord:{es:'Muestra',en:'Sample'},
  onlyDemo:{es:'Solo demo',en:'Demo only'},
  addProductAria:{es:'Agregar',en:'Add'},
  removeProductAria:{es:'Quitar de',en:'Remove from'},
  sampleLabel:{es:'Ejemplo de precio bajo',en:'Lowest sample price'},
  sachetLabel:{es:'Sachet 1 L',en:'1 L sachet'},
  lightLabel:{es:'Variedad liviana',en:'Light variety'},
  tetraLabel:{es:'Tetra Brik',en:'Tetra Brik'},
  pantryLabel:{es:'Almacén básico',en:'Pantry staple'},
  oilsLabel:{es:'Aceites y grasas',en:'Oils and fats'},
  variantMilk:{es:'Pasteurizada · sachet refrigerado · 3% tenor graso',en:'Pasteurised · refrigerated sachet · 3% milk fat'},
  variantLight:{es:'Parcialmente descremada · calcio activo · sachet',en:'Partly skimmed · active calcium · sachet'},
  variantTetra:{es:'Homogeneizada · larga vida · envase Tetra Brik',en:'Homogenised · shelf stable · Tetra Brik pack'},
  variantRice:{es:'Bolsa 1 kg · variedad largo fino',en:'1 kg bag · long grain variety'},
  variantOil:{es:'Botella 500 ml · aceite de oliva',en:'500 ml bottle · olive oil'},
  variantArmonia:{es:'UAT larga vida · 3% tenor graso · sachet',en:'UAT shelf stable · 3% milk fat · sachet'},
  fuelPlaceholder:{es:'Ingresá el valor',en:'Enter a value'},
  exampleDistance:{es:'Ej. 8',en:'e.g. 8'},
  exampleConsumption:{es:'Ej. 7.5',en:'e.g. 7.5'}
});
const COPY_ENTRIES=Object.values(COPY).sort((a,b)=>b.es.length-a.es.length);
const initialLanguage=()=>{try{const stored=window.localStorage.getItem(LANGUAGE_KEY);if(stored===LANGUAGES.EN||stored===LANGUAGES.ES_AR)return stored}catch(_){ }const preferred=(navigator.languages&&navigator.languages[0])||navigator.language||'';return String(preferred).toLowerCase().startsWith('es')?LANGUAGES.ES_AR:LANGUAGES.EN};
const copy=(key)=>{const value=COPY[key];return value?(state.language===LANGUAGES.EN?value.en:value.es):key};
const localizeMarkup=(markup)=>{if(state.language===LANGUAGES.ES_AR)return markup;const protectedValues=[];let html=markup;DEMO_PRODUCTS.forEach(product=>[product.brand,product.name,product.quantity,product.store].forEach(value=>{const token='__VALUEPILOT_TOKEN_'+protectedValues.length+'__';protectedValues.push([token,value]);html=html.split(value).join(token)}));html=COPY_ENTRIES.reduce((value,entry)=>value.split(entry.es).join(entry.en),html);return protectedValues.reduce((value,entry)=>value.split(entry[0]).join(entry[1]),html)};
const get=(id)=>DEMO_PRODUCTS.find(p=>p.id===id);
const money=(n)=>new Intl.NumberFormat(state.language===LANGUAGES.EN?'en-US':'es-AR',{maximumFractionDigits:0}).format(n);
const currency=(n)=>state.language===LANGUAGES.EN?'ARS '+money(n):'$'+money(n);
const icons=(name)=>`<svg aria-hidden="true"><use href="#i-${name}"></use></svg>`;
const safe=(value)=>String(value).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const state={page:'home',selected:'milk-serenisima-1l',from:'search',query:'leche',sort:'price',category:'Todos',saved:new Set(),basket:new Map(),detailQuantity:1,searchScroll:0,theme:'light',language:initialLanguage(),demo:true,history:[]};
const main=document.querySelector('#appMain');
const nav=document.querySelector('#bottomNav');
const bubble=document.querySelector('#basketBubble');
const toast=document.querySelector('#toast');
let timer;
const ariaLabel='Son datos de demostración, no precios verificados ni disponibles para comprar.';

function flash(message){toast.textContent=message;toast.hidden=false;clearTimeout(timer);timer=setTimeout(()=>{toast.hidden=true},2800)}
function basketCount(){return [...state.basket.values()].reduce((a,b)=>a+b,0)}
function basketSum(){return [...state.basket].reduce((a,[id,qty])=>a+(get(id)?.price||0)*qty,0)}
function saveScroll(){if(state.page==='search')state.searchScroll=window.scrollY||document.documentElement.scrollTop}
function navigate(page,options={}){if(page!==state.page)saveScroll();if(options.selected)state.selected=options.selected;if(options.from)state.from=options.from;if(page==='detail')state.detailQuantity=1;if(!options.skipHistory&&page!==state.page)state.history.push(state.page);state.page=page;render();if(options.restoreSearch&&page==='search')requestAnimationFrame(()=>window.scrollTo({top:state.searchScroll,behavior:'auto'}));else window.scrollTo(0,0)}
function back(){const previous=state.history.pop()||'search';navigate(previous,{skipHistory:true,restoreSearch:previous==='search'})}
function setTheme(next){state.theme=next;document.documentElement.classList.toggle('dark',next==='dark');document.querySelector('meta[name=theme-color]').setAttribute('content',next==='dark'?'#161714':'#fcf9f2');render()}
function toggleSaved(id){state.saved.has(id)?state.saved.delete(id):state.saved.add(id);render();flash(state.saved.has(id)?copy('toastSaved'):copy('toastRemoved'))}
function add(id,qty=1){state.basket.set(id,(state.basket.get(id)||0)+qty);render();flash(copy('toastBasket'))}
function changeQty(id,delta){const qty=(state.basket.get(id)||0)+delta;if(qty<=0)state.basket.delete(id);else state.basket.set(id,qty);render()}
function imageQuery(p){return [p.brand,p.name,p.quantity,'envase','Argentina'].filter(Boolean).join(' ')}
function openPhotos(id){const p=get(id);if(!p)return;const q=imageQuery(p);const url='https://www.google.com/search?tbm=isch&q='+encodeURIComponent(q); // Always a user-initiated external search.
  try{if(window.ValuePilotHost&&typeof window.ValuePilotHost.postMessage==='function'){
    window.ValuePilotHost.postMessage(JSON.stringify({type:'OPEN_GOOGLE_IMAGES',productId:p.id,query:q}));flash(copy('toastNativeGoogle'));return;
  }}catch(e){flash(copy('toastNativeError'));return}
  const opened=window.open(url,'_blank','noopener,noreferrer');
  if(!opened){const a=document.createElement('a');a.href=url;a.target='_blank';a.rel='noopener noreferrer';document.body.appendChild(a);a.click();a.remove()}
  flash(copy('toastExternalGoogle'));
}
function priceTag(product){return currency(product.price/product.liters)+'<span class="price-unit"> / '+(product.unit==='L'?(state.language===LANGUAGES.EN?'Litre':'Litro'):'kg')+'</span>'}
function persistLanguage(language){try{window.localStorage.setItem(LANGUAGE_KEY,language)}catch(_){ }try{if(window.ValuePilotHost&&typeof window.ValuePilotHost.postMessage==='function')window.ValuePilotHost.postMessage(JSON.stringify({type:'SET_LANGUAGE',language:language}))}catch(_){ }}
function setLanguage(next,options={}){const language=next===LANGUAGES.ES_AR?LANGUAGES.ES_AR:LANGUAGES.EN;const page=state.page;const scroll=window.scrollY||document.documentElement.scrollTop;if(state.language===language){updateChrome();return}state.language=language;if(options.persist!==false)persistLanguage(language);render();if(page==='search')requestAnimationFrame(()=>window.scrollTo({top:scroll,behavior:'auto'}))}
function updateChrome(){document.documentElement.lang=state.language;document.title=state.language===LANGUAGES.EN?'ValuePilot Next — Stitch prototype':'ValuePilot Next — Prototipo Stitch';const description=document.querySelector('meta[name=description]');if(description)description.setAttribute('content',state.language===LANGUAGES.EN?'ValuePilot Next — an offline visual prototype for the shopping experience':'ValuePilot Next — prototipo visual sin conexión con el servicio de precios');const set=(id,value)=>{const element=document.getElementById(id);if(element)element.textContent=value};set('demoRibbonTitle',copy('demoTitle'));set('demoRibbonMessage','· '+copy('demoMessage'));set('brandLocation','Buenos Aires (CABA)');set('navHomeLabel',copy('navHome'));set('navSearchLabel',copy('navSearch'));set('navBasketLabel',copy('navBasket'));set('navSavedLabel',copy('navSaved'));const brand=document.querySelector('#brandButton');if(brand)brand.setAttribute('aria-label',copy('backHome'));const theme=document.querySelector('#themeToggle');if(theme){theme.setAttribute('aria-label',copy('themeAria'));theme.setAttribute('title',copy('themeTitle'))}const settingsButton=document.querySelector('#settingsButton');if(settingsButton)settingsButton.setAttribute('aria-label',copy('preferences'));if(nav)nav.setAttribute('aria-label',copy('navLabel'));document.querySelectorAll('[data-language]').forEach(button=>{const active=button.dataset.language===state.language;button.setAttribute('aria-pressed',active?'true':'false');button.setAttribute('aria-label',button.dataset.language===LANGUAGES.EN?copy('english'):copy('spanishArgentina'))})}
function render(){main.innerHTML=localizeMarkup(({home:home,search:search,detail:detail,basket:basket,saved:saved,trip:trip,settings:settings})[state.page]());updateChrome();nav.querySelectorAll('button[data-nav]').forEach(btn=>{const active=btn.dataset.nav===state.page||((state.page==='detail'||state.page==='trip')&&btn.dataset.nav===(state.from==='basket'?'basket':'search'));btn.classList.toggle('active',active);btn.setAttribute('aria-current',active?'page':'false')});bubble.hidden=!basketCount();bubble.textContent=basketCount();}
function home(){return `<section class="content"><div class="home-intro"><div class="row"><span class="pill orange">● PRECIOS UNITARIOS NORMALIZADOS</span><span class="pill">CABA</span></div><h1 class="hero-title">Decidí mejor en cada compra.</h1><p class="hero-copy">Compará el valor por kilo o litro y planificá el recorrido, sin perder claridad.</p></div>
  <div class="search-bar home-search">${icons('search')}<input id="homeQuery" aria-label="Buscar un producto" placeholder="Buscar leche, arroz, aceite..." value="${safe(state.query)}"><button class="btn btn-primary" data-action="go-search">Buscar ${icons('arrow')}</button></div>
  <div class="home-stat"><div class="card card-dark"><div class="module-title">CANASTA DE PRUEBA <span class="pill" style="float:right;background:#ffffff26;color:inherit">${basketCount()} ítems</span></div><div><div class="number-lg">${basketCount()?currency(basketSum()):'Tu canasta'}</div><p class="micro">${basketCount()?'Precios ilustrativos · no verificables':'Agregá productos para comparar'}</p></div><button class="btn btn-outline" style="font-size:11px" data-nav="basket">Ver canasta ${icons('arrow')}</button></div>
  <div class="card"><div class="module-title">PLANIFICADOR DE RECORRIDOS</div><div class="number-lg" style="font-size:22px;margin-top:12px">Tu próximo viaje</div><p class="small">Calculá combustible, peajes y estacionamiento.</p><button class="btn btn-primary btn-fill" data-nav="trip">${icons('route')} Planificar viaje</button></div></div>
  <div class="section-header"><strong>Rubros de comparación</strong><button data-nav="search">Ver todos ${icons('arrow')}</button></div><div class="home-cats"><button class="chip active" data-action="category" data-category="Leche">Lácteos</button><button class="chip" data-action="category" data-category="Arroz">Almacén</button><button class="chip" data-action="category" data-category="Aceite">Aceites</button></div>
  <div class="section-header"><strong>Precios unitarios de ejemplo</strong><span class="micro">Solo demostración</span></div>
  <div class="stack">${[DEMO_PRODUCTS[1],DEMO_PRODUCTS[4]].map(p=>`<div class="card"><div class="split"><div><span class="pill">${safe(p.quantity)}</span><strong style="display:block;margin-top:7px;font-size:13px">${safe(p.brand)} · ${safe(p.name)}</strong></div><button class="bookmark-button" data-action="add" data-id="${p.id}" aria-label="Agregar ${safe(p.name)} a canasta">${icons('plus')}</button></div><div class="split" style="margin-top:8px"><span class="number-lg" style="font-size:20px">${currency(p.price)}</span><span class="micro">${safe(p.store)}</span></div></div>`).join('')}</div>
  <div class="preview-notice" style="margin-top:15px">${icons('info')}<span>Las cifras de esta vista son ficticias y sirven exclusivamente para evaluar la apariencia y los controles. No están conectadas a Firebase ni a precios reales.</span></div></section>`}
function filtered(){const q=state.query.toLocaleLowerCase('es-AR').trim();let data=DEMO_PRODUCTS.filter(p=>!q||[p.brand,p.name,p.variant,p.quantity,p.store].join(' ').toLocaleLowerCase('es-AR').includes(q));if(state.category!=='Todos')data=data.filter(p=>[p.brand,p.name,p.variant].join(' ').toLocaleLowerCase('es-AR').includes(state.category.toLocaleLowerCase('es-AR')));data.sort((a,b)=>state.sort==='price'?(a.price/a.liters-b.price/b.liters):state.sort==='name'?a.brand.localeCompare(b.brand):b.price/b.liters-a.price/a.liters);return data}
function search(){const items=filtered();return `<section class="content"><div class="page-top"><div><div class="eyebrow">BÚSQUEDA INTELIGENTE</div><h1 class="page-title">Encontrá el mejor valor</h1><div class="small">Buenos Aires (CABA) · Sin fotos nativas</div></div><span class="pill orange">ARS</span></div>
  <form class="search-bar" id="searchForm">${icons('search')}<input id="searchQuery" name="query" autocomplete="off" aria-label="Buscar producto" placeholder="Nombre, marca o cantidad" value="${safe(state.query)}"><button class="icon-button" type="button" id="clearQuery" aria-label="Borrar búsqueda">${icons('close')}</button><button class="btn btn-primary" type="submit" aria-label="Buscar">${icons('arrow')}</button></form>
  <div class="chip-row"><button class="chip active" id="sortButton" data-action="sort">${icons('sliders')} ${state.sort==='price'?'Menor $/unidad':state.sort==='name'?'Marca A–Z':'Mayor $/unidad'}</button><button class="chip ${state.category==='Todos'?'active':''}" data-action="category" data-category="Todos">Todos</button><button class="chip ${state.category==='Leche'?'active':''}" data-action="category" data-category="Leche">Leche</button><button class="chip ${state.category==='Arroz'?'active':''}" data-action="category" data-category="Arroz">Arroz</button><button class="chip ${state.category==='Aceite'?'active':''}" data-action="category" data-category="Aceite">Aceite</button></div>
  <div class="preview-notice">${icons('info')}<span><strong>VISTA DE DISEÑO · Datos inventados.</strong> Usamos ejemplos únicamente para comprobar el layout. Este prototipo no consulta precios, inventario ni comercios reales.</span></div>
  <div id="resultsArea">${results(items)}</div></section>`}
function results(items){return `${items.length?`<div class="section-header" style="margin:15px 0 10px"><strong>${items.length} ${items.length===1?'resultado':'resultados'} de muestra</strong><span class="micro">Por unidad comparable</span></div>`:''}${items.length?`<div class="product-list">${items.map((p,i)=>productCard(p,i)).join('')}</div>`:`<div class="empty-state">${icons('search')}<strong>No hay ejemplos coincidentes</strong><p>Probá «leche», «arroz» o «aceite». Esta pantalla funciona sin conexión a fuentes de precios.</p><button class="btn btn-primary" data-action="show-all">Ver ejemplos</button></div>`}`}
function productCard(p,i){return `<article class="product-card"><div class="product-tags"><span class="pill ${i===0?'green':''}">${i===0?'↘ Ejemplo con menor $/unidad':safe(p.label)}</span><span class="pill">${safe(p.quantity)}</span><button class="bookmark-button ${state.saved.has(p.id)?'is-saved':''}" data-action="save" data-id="${p.id}" aria-label="${state.saved.has(p.id)?'Quitar de':'Agregar a'} guardados: ${safe(p.name)}">${icons('bookmark')}</button></div>
  <button class="product-main" data-action="detail" data-id="${p.id}"><div class="product-brand">${safe(p.brand)} <span style="font-weight:450">· ${safe(p.name)}</span></div><div class="product-description">${safe(p.variant)}</div></button>
  <div class="price-slab" aria-label="Precio ficticio ${money(p.price)} pesos"><div><div class="slab-label">PRECIO UNITARIO · MUESTRA</div><div class="number-lg">${priceTag(p)}</div></div><div class="package-price">Total paquete<br><strong>${currency(p.price)}</strong></div></div>
  <div class="store-line"><span class="row">${icons('store')} ${safe(p.store)}</span><span>● Ficticio</span></div>
  <div class="product-actions"><button class="btn btn-soft" data-action="photos" data-id="${p.id}">${icons('camera')} Ver en Google Images</button><button class="btn btn-accent" data-action="add" data-id="${p.id}">${icons('basket')} Sumar ${currency(p.price)}</button></div></article>`}
function detail(){const p=get(state.selected)||DEMO_PRODUCTS[0];return `<section class="content"><button class="detail-back" data-action="back">${icons('back')} Volver a resultados</button><div class="detail-top"><span class="detail-brand">${safe(p.brand)} · EJEMPLO CABA</span><h1 class="detail-title">${safe(p.name)}</h1><div class="small">${safe(p.quantity)} · ${safe(p.variant)}</div></div>
  <div class="detail-image-placeholder"><span class="icon-circle">${icons('camera')}</span><strong style="font-size:14px">Sin fotos en el catálogo</strong><p>Podés examinar el envase en Google Images sin salir de tu recorrido de compra.</p><button class="btn btn-primary" data-action="photos" data-id="${p.id}">${icons('camera')} Ver fotos en Google Images ${icons('arrow')}</button><div class="disclaimer">Las imágenes externas pueden no coincidir con el producto, el envase o la cantidad exacta.</div></div>
  <div class="detail-metric"><div class="card"><div class="eyebrow" style="font-size:9px">PRECIO / ${p.unit==='L'?'LITRO':'KG'}</div><strong>${currency(p.price/p.liters)}</strong><div class="micro">Dato ficticio · no verificado</div></div><div class="card"><div class="eyebrow" style="font-size:9px">TOTAL PAQUETE</div><strong>${currency(p.price)}</strong><div class="micro">${safe(p.quantity)} · Muestra</div></div></div>
  <div class="section-header"><strong>Contexto de comparación</strong><span class="pill orange">Solo demo</span></div><div class="card"><div class="split"><div class="row">${icons('store')} <strong style="font-size:12px">${safe(p.store)}</strong></div><span class="pill">Ilustrativo</span></div><p class="small">La aplicación real mostrará precios, disponibilidad y tiendas únicamente cuando existan datos respaldados por las fuentes de ValuePilot.</p><div class="preview-notice">${icons('info')} No hay evidencia de precio real en este prototipo.</div></div>
  <div class="section-header"><strong>Canasta de demostración</strong></div><div class="card"><div class="split"><span class="small">Cantidad de paquetes</span><div class="qty-controls"><button data-action="detail-minus" aria-label="Reducir cantidad">${icons('minus')}</button><span>${state.detailQuantity}</span><button data-action="detail-plus" aria-label="Aumentar cantidad">${icons('plus')}</button></div></div><button class="btn btn-accent btn-fill" style="margin-top:13px" data-action="add-detail">${icons('basket')} Agregar ${state.detailQuantity} · ${currency(p.price*state.detailQuantity)}</button></div></section>`}
function basket(){const items=[...state.basket].filter(([id])=>get(id));return `<section class="content"><div class="page-top"><div><div class="eyebrow">CANASTA PRIVADA · DEMOSTRACIÓN</div><h1 class="page-title">Tu canasta</h1><div class="small">Sumá productos y compará cantidades.</div></div><span class="pill">${basketCount()} ítems</span></div>${items.length?`<div class="card"><div class="section-label" style="margin:0 0 9px">PRODUCTOS EN TU CANASTA</div>${items.map(([id,qty])=>{const p=get(id);return `<div class="basket-entry"><div class="item-label"><strong>${safe(p.brand)} · ${safe(p.name)}</strong><small>${safe(p.quantity)} · Precio de prueba ${currency(p.price)}</small></div><div class="qty-controls"><button data-action="basket-minus" data-id="${id}" aria-label="Quitar uno">${icons('minus')}</button><span>${qty}</span><button data-action="basket-plus" data-id="${id}" aria-label="Agregar uno">${icons('plus')}</button></div></div>`}).join('')}</div><div class="summary-total" style="margin-top:12px"><div class="split"><span style="font-size:12px">Total de ejemplo</span><span class="pill" style="background:#ffffff22;color:inherit">NO VERIFICADO</span></div><div class="number-lg" style="margin-top:10px">${currency(basketSum())}</div><div class="micro" style="color:#dadbd5">No representa una oferta, un ahorro ni una compra.</div></div><div class="stack" style="margin-top:13px"><button class="btn btn-primary btn-fill" data-nav="trip">${icons('route')} Estimar traslado</button><button class="btn btn-soft btn-fill" data-nav="search">Seguir buscando ${icons('arrow')}</button><button class="tap-link" data-action="clear-basket">Vaciar demostración</button></div>`:`<div class="empty-state">${icons('basket')}<strong>Tu canasta está vacía</strong><p>Elegí algunos productos de ejemplo para probar la experiencia. Los precios son ilustrativos.</p><button class="btn btn-primary" data-nav="search">Explorar productos ${icons('arrow')}</button></div>`}<div class="preview-notice" style="margin-top:14px">${icons('info')} Esta canasta funciona únicamente dentro del prototipo HTML. No modifica tu ValuePilot real, su base de datos ni las compras del teléfono.</div></section>`}
function saved(){const ids=[...state.saved].filter(id=>get(id));return `<section class="content"><div class="eyebrow">TU LISTA PERSONAL · DEMO</div><h1 class="page-title">Guardados</h1><p class="hero-copy">Tus productos favoritos, a mano.</p>${ids.length?`<div class="stack" style="margin-top:15px">${ids.map(id=>{const p=get(id);return `<div class="card saved-card"><div><strong style="font-size:13px">${safe(p.brand)} · ${safe(p.name)}</strong><div class="micro">${safe(p.quantity)} · Información ficticia</div></div><div class="row"><button class="icon-button" data-action="detail" data-id="${id}" aria-label="Abrir detalles">${icons('arrow')}</button><button class="bookmark-button is-saved" data-action="save" data-id="${id}" aria-label="Quitar de guardados">${icons('bookmark')}</button></div></div>`}).join('')}</div>`:`<div class="empty-state">${icons('bookmark')}<strong>Guardá lo que quieras recordar</strong><p>Tocá el marcador en los resultados. No se guardan productos reales.</p><button class="btn btn-primary" data-nav="search">Buscar productos ${icons('arrow')}</button></div>`}<div class="section-header"><strong>Otras herramientas</strong></div><div class="card stack"><button class="tap-link" data-nav="trip">${icons('route')} Estimador de recorrido</button><p class="micro">La versión completa conserva los flujos de observaciones, Watch y precios en la capa Kotlin existente.</p></div></section>`}
function trip(){return `<section class="content"><button class="detail-back" data-action="back">${icons('back')} Volver</button><div class="eyebrow">PLANIFICADOR · DEMOSTRACIÓN</div><h1 class="page-title">¿Cuánto cuesta llegar?</h1><p class="hero-copy">Calculá tu traslado con los datos que ingreses. No sugerimos rutas ni distancias inventadas.</p><div class="card" style="margin-top:16px"><form id="tripForm" class="stack"><label class="small">Distancia total del viaje (km)<input name="distance" inputmode="decimal" type="number" required min="0" step="0.1" placeholder="Ej. 8" class="inline-form-input"></label><label class="small">Consumo (litros / 100 km)<input name="consumption" inputmode="decimal" type="number" required min="0" step="0.1" placeholder="Ej. 7.5" class="inline-form-input"></label><label class="small">Precio del combustible (ARS / litro)<input name="fuel" inputmode="decimal" type="number" required min="0" step="0.1" placeholder="Ingresá el valor" class="inline-form-input"></label><label class="small">Peajes + estacionamiento (ARS)<input name="extras" inputmode="decimal" type="number" min="0" step="0.1" value="0" class="inline-form-input"></label><button type="submit" class="btn btn-primary btn-fill">${icons('route')} Calcular costo</button></form><div id="tripResult" hidden></div></div><div class="preview-notice" style="margin-top:14px">${icons('info')} El resultado usa únicamente tus entradas y no se registra ni compara con fuentes externas.</div></section>`}
function settings(){return `<section class="content"><button class="detail-back" data-action="back">${icons('back')} Volver</button><div class="eyebrow">PREFERENCIAS DE PREVISUALIZACIÓN</div><h1 class="page-title">Personalizá tu vista</h1><div class="settings-panel"><div class="setting-row"><span>Tema visual</span><select id="themeChoice" aria-label="Elegir tema"><option value="light" ${state.theme==='light'?'selected':''}>Claro</option><option value="dark" ${state.theme==='dark'?'selected':''}>Oscuro</option></select></div><div class="setting-row"><span>Idioma</span><div class="language-toggle" role="group" aria-label="Idioma"><button type="button" data-language="en" aria-pressed="${state.language===LANGUAGES.EN?'true':'false'}">EN</button><span aria-hidden="true">|</span><button type="button" data-language="es-AR" aria-pressed="${state.language===LANGUAGES.ES_AR?'true':'false'}">ES</button></div></div><div class="setting-row"><span>Mercado</span><span class="small">Buenos Aires (CABA)</span></div></div><div class="preview-notice" style="margin-top:15px">${icons('info')} Estas opciones solo afectan la maqueta HTML. No se guardan preferencias del ValuePilot instalado.</div></section>`}

// Event delegation for a small, auditable WebView surface.
document.addEventListener('click',(event)=>{const languageButton=event.target.closest('[data-language]');if(languageButton){event.preventDefault();setLanguage(languageButton.dataset.language);return}const el=event.target.closest('[data-action],[data-nav]');if(!el)return;event.preventDefault();const a=el.dataset.action,id=el.dataset.id;if(el.dataset.nav){navigate(el.dataset.nav);return}
 switch(a){case'go-search':{state.query=document.querySelector('#homeQuery')?.value||'leche';navigate('search');break}case'back':back();break;case'detail':navigate('detail',{selected:id,from:state.page});break;case'photos':openPhotos(id);break;case'add':add(id);break;case'add-detail':add(state.selected,state.detailQuantity);break;case'save':toggleSaved(id);break;case'basket-plus':changeQty(id,1);break;case'basket-minus':changeQty(id,-1);break;case'detail-plus':state.detailQuantity=Math.min(99,state.detailQuantity+1);render();break;case'detail-minus':state.detailQuantity=Math.max(1,state.detailQuantity-1);render();break;case'sort':state.sort=state.sort==='price'?'name':state.sort==='name'?'reverse':'price';render();break;case'category':state.category=el.dataset.category;if(state.page==='home'){state.query=state.category;state.category='Todos';navigate('search')}else render();break;case'show-all':state.query='';state.category='Todos';render();break;case'clear-basket':state.basket.clear();render();flash(copy('toastCleared'));break;}
});
document.querySelector('#settingsButton').addEventListener('click',()=>navigate('settings'));
document.querySelector('#themeToggle').addEventListener('click',()=>setTheme(state.theme==='light'?'dark':'light'));
document.addEventListener('change',(e)=>{if(e.target.id==='themeChoice')setTheme(e.target.value)});
document.addEventListener('submit',e=>{if(e.target.id==='searchForm'){e.preventDefault();const input=e.target.querySelector('input');state.query=input.value;const area=document.querySelector('#resultsArea');area.innerHTML=localizeMarkup(results(filtered()));document.activeElement.blur();return}if(e.target.id==='tripForm'){e.preventDefault();const fd=new FormData(e.target);const d=Number(fd.get('distance')),c=Number(fd.get('consumption')),f=Number(fd.get('fuel')),x=Number(fd.get('extras'));if([d,c,f,x].some(v=>!Number.isFinite(v)||v<0)){flash(copy('invalidInputs'));return}const cost=d*c*f/100+x;const result=document.querySelector('#tripResult');result.hidden=false;result.innerHTML=`<div class="summary-total" style="margin-top:14px"><div style="font-size:12px">Costo estimado con tus datos</div><div class="number-lg" style="margin-top:8px">${currency(cost)}</div><div class="micro" style="color:#ddd">${money(d)} km · ${Number((d*c/100).toFixed(2))} L + ${currency(x)} adicionales</div></div>`;return}});
document.addEventListener('input',(e)=>{if(e.target.id==='searchQuery'){state.query=e.target.value;document.querySelector('#resultsArea').innerHTML=results(filtered())}if(e.target.closest('#tripForm')){const result=document.querySelector('#tripResult');if(result)result.hidden=true}});
document.addEventListener('click',e=>{if(e.target.closest('#clearQuery')){state.query='';document.querySelector('#searchQuery').value='';document.querySelector('#resultsArea').innerHTML=results(filtered());document.querySelector('#searchQuery').focus()}});
document.addEventListener('keydown',e=>{if(e.key==='Escape'&&state.page!=='home'){back()}});
// Android host adapter: this is non-visual and keeps system back behavior inside the
// prototype without exposing the demo state to a privileged native interface.
window.ValuePilotHtmlPreview = Object.freeze({
  back:()=>{if(state.page==='home')return false;back();return true},
  page:()=>state.page,
  language:()=>state.language,
  setLanguage:(language)=>{setLanguage(language,{persist:false});return state.language},
});
// Security: this demo never persists, parses, or uses external image results.
render();
