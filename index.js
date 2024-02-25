const Discord = require('discord.js');
const sqlite3 = require('sqlite3').verbose();
const client = new Discord.Client();

const db = new sqlite3.Database('./userdata.db');

// Messages de bienvenue avec leurs probabilités, les rôles associés et les images
const welcomeMessages = [
    { message: "{mention} vient d'être invoqué sur **Camélia Studio** ! Souhaitons-lui la bienvenue ! Il s'agit d'un personnage de rareté **B**. Dommage, nous aurons plus de chance la prochaine fois...", role: "Rang B", probability: 80, image: "https://concepts.esenjin.xyz/cyla/v2/file/11C108.png" },
    { message: "{mention} vient d'être invoqué sur **Camélia Studio** ! Souhaitons-lui la bienvenue ! Il s'agit d'un personnage de rareté **A**. C'est plutôt une bonne pioche !", role: "Rang A", probability: 15, image: "https://concepts.esenjin.xyz/cyla/v2/file/732316.png" },
    { message: "{mention} vient d'être invoqué sur **Camélia Studio** ! Souhaitons-lui la bienvenue ! Il s'agit d'un personnage de rareté **S**. On a vraiment de la chance aujourd'hui !!", role: "Rang S", probability: 4, image: "https://concepts.esenjin.xyz/cyla/v2/file/D6E3E1.png" },
    { message: "{mention} vient d'être invoqué sur **Camélia Studio** ! Souhaitons-lui la bienvenue ! Il s'agit d'un personnage de rareté **S+**. Incroyable ! On vient de tomber sur la perle rare !", role: "Rang S+", probability: 1, image: "https://concepts.esenjin.xyz/cyla/v2/file/6B6CE3.png" }
];

let welcomeChannel; // Canal où publier les messages de bienvenue
let farewellChannel; // Canal où publier les messages d'adieu

// Création de la table dans la base de données
db.serialize(() => {
    db.run(`CREATE TABLE IF NOT EXISTS userdata (
        userid TEXT PRIMARY KEY,
        message TEXT,
        role TEXT,
        image TEXT
    )`);
});

// Fonction pour choisir un message de bienvenue selon les probabilités
function chooseWelcomeMessage() {
    const random = Math.random() * 100; // Random entre 0 et 100
    let cumulativeProbability = 0;
    for (const { message, role, image } of welcomeMessages) {
        cumulativeProbability += probability;
        if (random < cumulativeProbability) {
            return { message, role, image };
        }
    }
}

// Commande "/gachaoptions"
client.on('message', message => {
    if (message.member.hasPermission('ADMINISTRATOR') && message.content.startsWith('/gachaoptions')) {
        const args = message.content.split(' ');
        if (args.length === 1) {
            // Afficher les options actuelles
            message.channel.send(`Options actuelles : 
            - Canal de bienvenue : ${welcomeChannel ? welcomeChannel : "non défini"}
            - Canal d'adieu : ${farewellChannel ? farewellChannel : "non défini"}
            `);
        } else if (args.length === 3 && args[1] === 'bienvenue') {
            // Définir le canal de bienvenue
            const channelID = args[2].replace(/<#|>/g, '');
            welcomeChannel = message.guild.channels.cache.get(channelID);
            message.channel.send(`Canal de bienvenue défini sur : ${welcomeChannel}`);
        } else if (args.length === 3 && args[1] === 'adieu') {
            // Définir le canal d'adieu
            const channelID = args[2].replace(/<#|>/g, '');
            farewellChannel = message.guild.channels.cache.get(channelID);
            message.channel.send(`Canal d'adieu défini sur : ${farewellChannel}`);
        } else if (args.length === 3 && args[1] === 'messageadieu') {
            // Définir le message d'adieu
            farewellMessage = args.slice(2).join(' ');
            message.channel.send(`Message d'adieu défini sur : ${farewellMessage}`);
        } else if (args.length === 3 && args[1] === 'imageadieu') {
            // Définir l'image d'adieu
            farewellImage = args[2];
            message.channel.send(`Image d'adieu définie sur : ${farewellImage}`);
        } else if (args.length === 3 && args[1] === 'bienvenueimage') {
            // Définir l'image de bienvenue
            welcomeImage = args[2];
            message.channel.send(`Image de bienvenue définie sur : ${welcomeImage}`);
        } else if (args.length === 3 && args[1] === 'rangauto') {
            // Définir la commande pour le rang automatique
            autorangCommand = args[2];
            message.channel.send(`Commande pour le rang automatique définie sur : ${autorangCommand}`);
        } else if (args.length === 3 && args[1] === 'rangs') {
            // Afficher les rôles et leurs messages associés
            message.channel.send(`Messages de bienvenue et rôles associés : 
            - Rang B : ${welcomeMessages.find(msg => msg.role === "Rang B").message}
            - Rang A : ${welcomeMessages.find(msg => msg.role === "Rang A").message}
            - Rang S : ${welcomeMessages.find(msg => msg.role === "Rang S").message}
            - Rang S+ : ${welcomeMessages.find(msg => msg.role === "Rang S+").message}
            `);
        } else if (args.length === 5 && args[1] === 'ajoutermessage') {
            // Ajouter un nouveau message de bienvenue
            const newRole = args[2];
            const newProbability = parseInt(args[3]);
            const newMessage = args.slice(4).join(' ');
            welcomeMessages.push({ message: newMessage, role: newRole, probability: newProbability });
            message.channel.send(`Nouveau message de bienvenue ajouté pour le rôle ${newRole} avec une probabilité de ${newProbability}%.`);
        } else if (args.length === 4 && args[1] === 'modifierprobabilite') {
            // Modifier la probabilité d'un message de bienvenue existant
            const roleName = args[2];
            const newProbability = parseInt(args[3]);
            const index = welcomeMessages.findIndex(msg => msg.role === roleName);
            if (index !== -1) {
                // Calculer la somme des probabilités actuelles
                let currentTotal = welcomeMessages.reduce((total, msg) => total + msg.probability, 0);
                // Calculer la différence entre la nouvelle probabilité et l'ancienne
                let difference = newProbability - welcomeMessages[index].probability;
                // Mettre à jour la probabilité
                welcomeMessages[index].probability = newProbability;
                // Vérifier si le total est égal à 100%
                if (currentTotal + difference !== 100) {
                    message.channel.send(`La somme des probabilités n'est pas égale à 100%. Veuillez modifier une autre probabilité pour ajuster.`);
                } else {
                    message.channel.send(`Probabilité du message de bienvenue pour le rôle ${roleName} modifiée à ${newProbability}%.`);
                }
            } else {
                message.channel.send(`Le rôle ${roleName} n'existe pas.`);
            }
        } else if (args.length === 4 && args[1] === 'changerrole') {
            // Changer le rôle associé à un message de bienvenue existant
            const roleName = args[2];
            const newRoleName = args[3];
            const index = welcomeMessages.findIndex(msg => msg.role === roleName);
            if (index !== -1) {
                welcomeMessages[index].role = newRoleName;
                message.channel.send(`Rôle associé au message de bienvenue pour le rôle ${roleName} changé en ${newRoleName}.`);
            } else {
                message.channel.send(`Le rôle ${roleName} n'existe pas.`);
            }
        } else {
            message.channel.send("Options disponibles : \n- `/gachaoptions bienvenue <nom_du_canal>` pour définir le canal de bienvenue\n- `/gachaoptions adieu <nom_du_canal>` pour définir le canal d'adieu\n- `/gachaoptions messageadieu <message>` pour définir le message d'adieu\n- `/gachaoptions imageadieu <lien_de_l'image>` pour définir l'image d'adieu\n- `/gachaoptions bienvenueimage <lien_de_l'image>` pour définir l'image de bienvenue\n- `/gachaoptions rangauto <commande>` pour définir la commande pour le rang automatique\n- `/gachaoptions rangs` pour afficher les rôles et leurs messages associés\n- `/gachaoptions ajoutermessage <role> <probabilité> <message>` pour ajouter un nouveau message de bienvenue\n- `/gachaoptions modifierprobabilite <role> <probabilité>` pour modifier la probabilité d'un message de bienvenue\n- `/gachaoptions changerrole <role> <nouveau_role>` pour changer le rôle associé à un message de bienvenue");
        }
    }
});

client.on('guildMemberAdd', member => {
    // Vérifier si l'utilisateur est déjà dans la base de données
    db.get(`SELECT * FROM userdata WHERE userid = ?`, member.id, (err, row) => {
        if (err) {
            console.error(err);
            return;
        }
        if (row) {
            // Utiliser les données stockées
            const { message, role, image } = row;
            // Envoi du message de bienvenue dans le canal approprié
            const welcomeMessage = message.replace("{mention}", member.user);
            const embed = new Discord.MessageEmbed()
                .setColor('#0099ff')
                .setDescription(welcomeMessage)
                .setImage(image);
            if (welcomeChannel) {
                welcomeChannel.send(embed);
            }
            // Attribution automatique du rôle
            const guildRole = member.guild.roles.cache.find(guildRole => guildRole.name === role);
            if (guildRole) {
                member.roles.add(guildRole);
            }
        } else {
            // Choix du message de bienvenue, du rôle associé et de l'image
            const { message, role, image } = chooseWelcomeMessage();
            // Envoi du message de bienvenue dans le canal approprié
            const welcomeMessage = message.replace("{mention}", member.user);
            const embed = new Discord.MessageEmbed()
                .setColor('#0099ff')
                .setDescription(welcomeMessage)
                .setImage(image);
            if (welcomeChannel) {
                welcomeChannel.send(embed);
            }
            // Attribution automatique du rôle
            const guildRole = member.guild.roles.cache.find(guildRole => guildRole.name === role);
            if (guildRole) {
                member.roles.add(guildRole);
            }
            // Stocker les données de bienvenue dans la base de données
            db.run(`INSERT INTO userdata (userid, message, role, image) VALUES (?, ?, ?, ?)`, [member.id, message, role, image], err => {
                if (err) {
                    console.error(err);
                }
            });
        }
    });
});

client.on('guildMemberRemove', member => {
    // Message d'adieu
    const farewellMessage = `Oh non ! ${member.user} n'est plus utile dans la méta actuelle et quitte notre équipe. Espérons qu'une prochaine mise à jour lui soit favorable !`;
    const embed = new Discord.MessageEmbed()
        .setColor('#0099ff')
        .setDescription(farewellMessage)
        .setImage("https://concepts.esenjin.xyz/cyla/v2/file/EDF7B4.gif");
    if (farewellChannel) {
        farewellChannel.send(embed);
    }
    // Supprimer les données de bienvenue de la base de données
    db.run(`DELETE FROM userdata WHERE userid = ?`, member.id, err => {
        if (err) {
            console.error(err);
        }
    });
});

// Connexion du bot à Discord
client.login('TOKEN_DU_BOT');
