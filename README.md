# Gachamélia


Transforme ton serveur Discord en *gacha* géant !


**Gachamélia** est un bot Discord qui va transformer ton serveur en un véritable *gacha* ! Cet outil est développé par la branche **CILA** de l'association **Camélia Studio**.

## Configuration

Le bot utilise l'API du backoffice comme source de vérité.

Variables requises :

- `BOT_TOKEN`
- `API_BASE_URL`
- `API_CLIENT_ID`
- `API_CLIENT_SECRET`
- `XP_EMOJI`
- `APP_VERSION`
- `APP_DESCRIPTION`

Variables runtime optionnelles :

- `CATALOGUE_REFRESH_INTERVAL_MINUTES` : fréquence de synchronisation des catalogues, `5` par défaut.
- `MEMBER_SYNC_CONCURRENCY` : nombre maximal d'appels membres concurrents pour l'ensemble des serveurs, `4` par défaut.
- `SYNC_BOT_MEMBERS` : inclut les comptes bots dans la réconciliation lorsqu'il vaut `true`, `false` par défaut.

Un serveur dont l'API retourne `validation.ready=false` reste connecté, mais les fonctionnalités membres restent désactivées jusqu'à ce que son catalogue soit prêt.


Liens utiles :


- Site web Camélia Studio : https://camelia-studio.org
- Site web CILA : https://cila.camelia-studio.org
- Notre serveur Discord : https://discord.gg/nBuZ9vJ


## Fonctionnalités actuelles

### Message de bienvenue
![image](https://concepts.esenjin.xyz/cyla/v2/file/62D207.png)


Un message de bienvenue qui *invoque* l'utilisateur qui vient d'arriver sur le serveur. Cela lui détermine une rareté aléatoire (de 1★ à 5★) et lui attribue un *rôle* ainsi qu'un *élément*. La phrase d'accueil est sélectionnée au hasard parmi une liste (qui dépend de la rareté obtenue).


### Message d'au-revoir
![image](https://concepts.esenjin.xyz/cyla/v2/file/B4CAA0.png)


Un message d'au-revoir lorsque quelqu'un quitte le serveur. La phrase d'adieu est sélectionnée au hasard parmi une liste (qui dépend de la rareté obtenue).


### Fiche de personnage
![image](https://concepts.esenjin.xyz/cyla/v2/file/339FF0.png)


La fiche avec les caratéristiques principales du personnage. Peut s'obtenir avec la commande `/ficheperso`.
